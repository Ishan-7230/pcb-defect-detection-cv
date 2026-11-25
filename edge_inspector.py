#!/usr/bin/env python3
"""Offline Raspberry Pi reference inspector: YOLO, optional anomaly memory, SQLite, optional Llama."""
from __future__ import annotations
import argparse, concurrent.futures, hashlib, json, sqlite3, subprocess, time, uuid
from datetime import datetime, timezone
from pathlib import Path
import cv2, numpy as np
from ultralytics import YOLO

SOPS = {
    "crazing": "Hold for magnified inspection; review material stress and process conditions.",
    "crack": "Hold and inspect crack depth/position; review process and adjacent components.",
    "inclusion": "Hold for review; inspect material quality and surface cleanliness.",
    "pitted_surface": "Review surface finishing and corrosion-prevention process.",
    "scratch": "Inspect handling contact points, rollers, fixtures and transport surfaces.",
    "unknown_anomaly": "Isolate the component and inspect the highlighted region manually.",
    "image_quality": "Recapture after correcting focus, exposure, lighting or camera stability.",
    "default": "Hold for operator review; verify the defect and compare the production batch.",
}

def image_quality(frame, dark_min=20.0, bright_max=245.0, blur_min=15.0):
    gray=cv2.cvtColor(frame,cv2.COLOR_BGR2GRAY)
    brightness=float(gray.mean())
    sharpness=float(cv2.Laplacian(gray,cv2.CV_64F).var())
    flags=[]
    if brightness<dark_min: flags.append("underexposed")
    if brightness>bright_max: flags.append("overexposed")
    if sharpness<blur_min: flags.append("blurry")
    return {"brightness":brightness,"sharpness":sharpness,"flags":flags,"passed":not flags}

def open_db(path):
    db=sqlite3.connect(path)
    db.execute("""CREATE TABLE IF NOT EXISTS inspections(
        event_id TEXT PRIMARY KEY, timestamp_utc TEXT, product_id TEXT,
        decision TEXT, defects_json TEXT, anomaly_score REAL, anomaly_alarm INTEGER,
        quality_json TEXT, model_sha TEXT, latency_ms REAL, sop TEXT, llama_output TEXT,
        operator_feedback TEXT)""")
    db.commit()
    # Add the feedback column when upgrading a database created by an older app version.
    existing_columns={row[1] for row in db.execute("PRAGMA table_info(inspections)")}
    if "operator_feedback" not in existing_columns:
        db.execute("ALTER TABLE inspections ADD COLUMN operator_feedback TEXT")
        db.commit()
    return db

def file_sha256(path):
    h=hashlib.sha256()
    with open(path,"rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""):
            h.update(chunk)
    return h.hexdigest()[:16]

def run_llama_advisory(runner, model_path, tokenizer_path, event, sop, timeout=120):
    payload={"role":"quality_control_advisor","locked_decision":event["decision"],
             "inspection_facts":event,"approved_sop":sop,
             "instructions":["Do not change the inspection decision.","Use only supplied facts and approved SOP.","Do not invent causes.","Give a short checklist; no machine actuation."]}
    prompt=json.dumps(payload,separators=(",",":"))
    command=[str(runner),"--model_path",str(model_path),"--tokenizer_path",str(tokenizer_path),
            "--seq_len","256","--prompt",prompt]
    result=subprocess.run(command,capture_output=True,text=True,timeout=timeout,check=False)
    if result.returncode!=0:
        return "EXECUTORCH_ERROR: "+(result.stderr or result.stdout)[-1200:]
    return result.stdout.strip()[-3000:]

class OptionalAnomaly:
    """ONNX MobileNet patch encoder + cosine memory bank for same-domain anomaly checks."""
    def __init__(self, encoder_path, bank_path, expected_domain):
        import onnxruntime as ort
        self.session=ort.InferenceSession(str(encoder_path),providers=["CPUExecutionProvider"])
        archive=np.load(bank_path,allow_pickle=False)
        self.bank=archive["bank"].astype(np.float32)
        self.threshold=float(archive["threshold"])
        self.domain=str(archive["domain"].item())
        if self.domain!=expected_domain:
            raise ValueError(f"Anomaly bank domain {self.domain!r} does not match requested product {expected_domain!r}.")
        self.input_name=self.session.get_inputs()[0].name
    def score(self, frame_bgr):
        image=cv2.cvtColor(frame_bgr,cv2.COLOR_BGR2RGB)
        image=cv2.resize(image,(224,224),interpolation=cv2.INTER_AREA).astype(np.float32)/255.0
        mean=np.array([.485,.456,.406],np.float32); std=np.array([.229,.224,.225],np.float32)
        tensor=((image-mean)/std).transpose(2,0,1)[None].astype(np.float32)
        feature=self.session.run(None,{self.input_name:tensor})[0][0]
        c,h,w=feature.shape
        desc=feature.transpose(1,2,0).reshape(-1,c)
        desc=desc/np.maximum(np.linalg.norm(desc,axis=1,keepdims=True),1e-8)
        distances=1.0-(desc@self.bank.T).max(axis=1)
        k=max(1,int(np.ceil(.05*len(distances))))
        score=float(np.mean(np.partition(distances,-k)[-k:]))
        heat=distances.reshape(h,w)
        return score,heat

def main():
    parser=argparse.ArgumentParser(description="Offline quality inspection app (human-reviewed candidate decisions)")
    parser.add_argument("--weights",help="YOLO .pt or ONNX model; optional only with --disable-detector")
    parser.add_argument("--detector-product",default="steel_gc10")
    parser.add_argument("--disable-detector",action="store_true")
    parser.add_argument("--product",default="steel_gc10")
    parser.add_argument("--source",default="0",help="camera index, image file, or video file")
    parser.add_argument("--db",default="inspections.sqlite3")
    parser.add_argument("--conf",type=float,default=.25)
    parser.add_argument("--imgsz",type=int,default=640)
    parser.add_argument("--interval",type=float,default=.5)
    parser.add_argument("--view",action="store_true")
    parser.add_argument("--evidence-dir",type=Path)
    parser.add_argument("--anomaly-encoder",type=Path)
    parser.add_argument("--anomaly-bank",type=Path)
    parser.add_argument("--llama-runner",type=Path)
    parser.add_argument("--llama-model",type=Path)
    parser.add_argument("--llama-tokenizer",type=Path)
    args=parser.parse_args()

    detector=None; model_sha="none"
    if not args.disable_detector:
        if not args.weights: parser.error("Provide --weights or use --disable-detector")
        if args.product!=args.detector_product:
            raise ValueError("Product identifier does not match detector training domain; refusing to run.")
        weights=Path(args.weights)
        if not weights.is_file(): raise FileNotFoundError(weights)
        detector=YOLO(str(weights),task="detect"); model_sha=file_sha256(weights)
    anomaly=None
    if bool(args.anomaly_encoder)!=bool(args.anomaly_bank):
        parser.error("Provide both --anomaly-encoder and --anomaly-bank, or neither.")
    if args.anomaly_encoder and args.anomaly_bank:
        anomaly=OptionalAnomaly(args.anomaly_encoder,args.anomaly_bank,expected_domain=args.product)

    db=open_db(args.db)
    if args.evidence_dir: args.evidence_dir.mkdir(parents=True,exist_ok=True)
    llama_paths=(args.llama_runner,args.llama_model,args.llama_tokenizer)
    llama_ready=all(x is not None and x.is_file() for x in llama_paths)
    if any(x is not None for x in llama_paths) and not llama_ready:
        print("Warning: Llama advisory paths are incomplete; advisory inference is disabled.")
    pool=concurrent.futures.ThreadPoolExecutor(max_workers=1) if llama_ready else None
    pending=None
    source=int(args.source) if str(args.source).isdigit() else str(args.source)
    image_exts={".jpg",".jpeg",".png",".bmp",".webp"}
    single_image=isinstance(source,str) and Path(source).suffix.lower() in image_exts
    capture=None if single_image else cv2.VideoCapture(source)
    if not single_image and not capture.isOpened():
        db.close();
        raise RuntimeError(f"Camera/video unavailable: {source}")
    last_time=-1e9
    try:
        while True:
            if single_image:
                frame=cv2.imread(source)
                if frame is None: raise RuntimeError(f"Cannot read image: {source}")
            else:
                ok,frame=capture.read()
                if not ok: break
            if not single_image and time.monotonic()-last_time<args.interval:
                time.sleep(0.005)
                continue
            last_time=time.monotonic(); start=time.perf_counter(); quality=image_quality(frame)
            result=None; defects=[]; anomaly_score=None; anomaly_alarm=False; heatmap=None
            if quality["passed"]:
                if detector is not None:
                    result=detector.predict(frame,conf=args.conf,imgsz=args.imgsz,device="cpu",verbose=False)[0]
                    for box in result.boxes:
                        cls_id=int(box.cls.item()); x1,y1,x2,y2=[float(v) for v in box.xyxy[0].tolist()]
                        height,width=frame.shape[:2]
                        area_ratio=max(0.0,x2-x1)*max(0.0,y2-y1)/(height*width)
                        defects.append({"class_id":cls_id,"class_name":detector.names[cls_id],
                                      "confidence":round(float(box.conf.item()),4),
                                      "bbox_xyxy":[round(x1,1),round(y1,1),round(x2,1),round(y2,1)],
                                      "area_ratio":round(area_ratio,6)})
                if anomaly is not None:
                    anomaly_score,heatmap=anomaly.score(frame)
                    anomaly_alarm=anomaly_score>anomaly.threshold
            if not quality["passed"]:
                decision="RECAPTURE"; sop=SOPS["image_quality"]
            elif defects:
                decision="HOLD"; sop=SOPS.get(str(defects[0]["class_name"]).lower(),SOPS["default"])
            elif anomaly_alarm:
                decision="REVIEW"; sop=SOPS["unknown_anomaly"]
            elif anomaly is not None:
                decision="ACCEPT_CANDIDATE"; sop="No anomaly signal; continue periodic QA audits."
            else:
                decision="REVIEW_UNVERIFIED"; sop="No product-specific anomaly model is loaded; do not automatically pass clean items."
            event_id=str(uuid.uuid4()); timestamp=datetime.now(timezone.utc).isoformat()
            latency_ms=(time.perf_counter()-start)*1000
            event={"event_id":event_id,"timestamp_utc":timestamp,"product_id":args.product,
                   "decision":decision,"quality":quality,"defects":defects,"anomaly_score":anomaly_score,
                   "anomaly_alarm":bool(anomaly_alarm),"model_sha":model_sha,"latency_ms":round(latency_ms,2)}
            db.execute("INSERT INTO inspections(event_id,timestamp_utc,product_id,decision,defects_json,anomaly_score,anomaly_alarm,quality_json,model_sha,latency_ms,sop,llama_output,operator_feedback) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    (event_id,timestamp,args.product,decision,json.dumps(defects),anomaly_score,int(anomaly_alarm),
                     json.dumps(quality),model_sha,latency_ms,sop,None,None)); db.commit()
            print(json.dumps({**event,"sop":sop},default=str))
            if pool and decision!="ACCEPT_CANDIDATE" and (pending is None or pending.done()):
                def worker(eid,payload,sop_text):
                    try: output=run_llama_advisory(args.llama_runner,args.llama_model,args.llama_tokenizer,payload,sop_text)
                    except Exception as exc: output="EXECUTORCH_ERROR: "+str(exc)
                    with sqlite3.connect(args.db) as worker_db:
                        worker_db.execute("UPDATE inspections SET llama_output=? WHERE event_id=?",(output,eid)); worker_db.commit()
                    print("Llama advisory (not decision input): ",output[:500])
                pending=pool.submit(worker,event_id,event,sop)
            if args.evidence_dir: cv2.imwrite(str(args.evidence_dir/f"{event_id}.jpg"),frame)
            if args.view:
                shown=result.plot() if result is not None else frame.copy()
                cv2.putText(shown,decision,(8,28),cv2.FONT_HERSHEY_SIMPLEX,.8,(0,0,255),2)
                if heatmap is not None:
                    hm=cv2.resize(heatmap.astype(np.float32),(frame.shape[1],frame.shape[0]))
                    hm=cv2.normalize(hm,None,0,255,cv2.NORM_MINMAX).astype(np.uint8)
                    overlay=cv2.applyColorMap(hm,cv2.COLORMAP_JET)
                    shown=cv2.addWeighted(shown,.7,overlay,.3,0)
                cv2.imshow("Offline QC Inspector (q=quit)",shown)
                if cv2.waitKey(1)&0xff==ord("q"): break
            if single_image: break
    finally:
        if capture is not None: capture.release()
        if args.view: cv2.destroyAllWindows()
        if pool: pool.shutdown(wait=True)
        db.close()

if __name__=="__main__":
    main()
