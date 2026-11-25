"""Reusable helpers for the Industry 4.0 quality-inspection Colab notebook."""
from __future__ import annotations
import math, random, shutil
from collections import Counter, defaultdict
from pathlib import Path
import numpy as np
import cv2
import yaml
from PIL import Image

IMAGE_EXTENSIONS = {'.jpg', '.jpeg', '.png', '.bmp', '.tif', '.tiff', '.webp'}
SPLIT_ALIASES = {'train':'train', 'training':'train', 'val':'val', 'valid':'val', 'validation':'val', 'test':'test', 'testing':'test'}


def list_images(root):
    root = Path(root)
    return sorted(p for p in root.rglob('*') if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS) if root.exists() else []


def class_root_candidates(root):
    """Find ImageFolder-like roots: 2+ immediate folders each containing images."""
    root = Path(root)
    if not root.exists():
        return []
    directories = [root] + [p for p in root.rglob('*') if p.is_dir()]
    candidates = []
    for parent in directories:
        try:
            subdirs = [d for d in parent.iterdir() if d.is_dir()]
        except OSError:
            continue
        classes = []
        for d in subdirs:
            images = [p for p in d.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS]
            if images:
                classes.append(d)
        if len(classes) >= 2:
            count = sum(sum(1 for p in d.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS) for d in classes)
            split = None
            for ancestor in [parent] + list(parent.parents):
                if ancestor.name.lower() in SPLIT_ALIASES:
                    split = SPLIT_ALIASES[ancestor.name.lower()]
                    break
            candidates.append({'root': parent, 'classes': classes, 'count': count, 'split': split})
    return candidates


def _image_files_by_class(root):
    root = Path(root)
    if not root.exists():
        raise FileNotFoundError(root)
    mapping = {}
    for class_dir in sorted(p for p in root.iterdir() if p.is_dir()):
        images = sorted(p for p in class_dir.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS)
        if images:
            mapping[class_dir.name] = images
    if len(mapping) < 2:
        raise ValueError(f'Expected >=2 image class folders under {root}')
    return mapping


def _copy_class_mapping(mapping, dest_split, class_name):
    target = Path(dest_split) / class_name
    target.mkdir(parents=True, exist_ok=True)
    for idx, source in enumerate(mapping):
        shutil.copy2(source, target / f'{idx:06d}_{source.name}')


def _split_files(files, seed, train_ratio=.70, val_ratio=.15):
    files = list(files)
    random.Random(seed).shuffle(files)
    n = len(files)
    if n < 3:
        raise ValueError(f'Each classification class requires at least 3 images; got {n}')
    n_train = max(1, int(round(n * train_ratio)))
    n_val = max(1, int(round(n * val_ratio)))
    n_test = n - n_train - n_val
    if n_test < 1:
        n_test = 1
        if n_train > 1:
            n_train -= 1
        else:
            n_val -= 1
    return {'train': files[:n_train], 'val': files[n_train:n_train+n_val], 'test': files[n_train+n_val:]}


def prepare_classification_splits(raw_root, processed_root, seed=42, train_ratio=.70, val_ratio=.15):
    """Discover class folders and prepare train/val/test. Existing named splits are preserved.


    When only train+validation exist, training is split into train/val and the
    original validation set becomes test to avoid using it twice.
    """
    raw_root, processed_root = Path(raw_root), Path(processed_root)
    candidates = class_root_candidates(raw_root)
    if not candidates:
        raise ValueError(f'No class-folder images found beneath {raw_root}. Expected folders like class_name/image.jpg')
    split_candidates = [c for c in candidates if c['split']]
    best_per_split = {}
    for candidate in split_candidates:
        split = candidate['split']
        if split not in best_per_split or candidate['count'] > best_per_split[split]['count']:
            best_per_split[split] = candidate
    classes = None
    split_sources = {}
    if 'train' in best_per_split and ('val' in best_per_split or 'test' in best_per_split):
        train_map = _image_files_by_class(best_per_split['train']['root'])
        classes = sorted(train_map)
        for split, candidate in best_per_split.items():
            class_map = _image_files_by_class(candidate['root'])
            if sorted(class_map) != classes:
                raise ValueError(f'Class folders differ between splits ({split}); inspect dataset manually.')
            split_sources[split] = class_map
    else:
        flat_candidate = max((c for c in candidates if not c['split']), key=lambda c:c['count'], default=None)
        if flat_candidate is None:
            # Fall back to largest class-root candidate, but never mix split roots.
            flat_candidate = max(candidates, key=lambda c:c['count'])
        classes = sorted(_image_files_by_class(flat_candidate['root']))
        split_sources['flat'] = _image_files_by_class(flat_candidate['root'])

    if processed_root.exists():
        shutil.rmtree(processed_root)
    processed_root.mkdir(parents=True, exist_ok=True)

    if 'flat' in split_sources:
        for class_name, files in split_sources['flat'].items():
            splits = _split_files(files, seed, train_ratio, val_ratio)
            for split_name, split_files in splits.items():
                _copy_class_mapping(split_files, processed_root / split_name, class_name)
    else:
        # Case: a fully supplied train/val/test structure.
        if {'train','val','test'}.issubset(split_sources):
            for split_name, class_map in split_sources.items():
                for class_name, files in class_map.items():
                    _copy_class_mapping(files, processed_root / split_name, class_name)
        # Case: train+val, but no independent test. Carve val from train and reserve
        # original val for test. This is a pragmatic benchmark split, not factory batch split.
        elif 'train' in split_sources and 'val' in split_sources and 'test' not in split_sources:
            for class_name, files in split_sources['train'].items():
                train_val = _split_files(files, seed, train_ratio=.85, val_ratio=.15)
                for split_name in ('train','val'):
                    _copy_class_mapping(train_val[split_name], processed_root / split_name, class_name)
                _copy_class_mapping(split_sources['val'][class_name], processed_root / 'test', class_name)
        # Case: train+test. Split train into train/val and retain official test.
        elif 'train' in split_sources and 'test' in split_sources:
            for class_name, files in split_sources['train'].items():
                train_val = _split_files(files, seed, train_ratio=.85, val_ratio=.15)
                for split_name in ('train','val'):
                    _copy_class_mapping(train_val[split_name], processed_root / split_name, class_name)
                _copy_class_mapping(split_sources['test'][class_name], processed_root / 'test', class_name)
        else:
            # Only one split root found; use it as a flat class-folder dataset.
            largest = max(candidates, key=lambda c:c['count'])
            class_map = _image_files_by_class(largest['root'])
            for class_name, files in class_map.items():
                splits = _split_files(files, seed, train_ratio, val_ratio)
                for split_name, split_files in splits.items():
                    _copy_class_mapping(split_files, processed_root / split_name, class_name)
    # Final structural check for all splits.
    for split in ('train','val','test'):
        split_dir = processed_root / split
        if not split_dir.exists() or not any(p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS for p in split_dir.rglob('*')):
            raise RuntimeError(f'Classification split {split!r} is empty at {split_dir}')
    print('Classification data ready:', processed_root)
    return processed_root


def find_yolo_yaml(root):
    root = Path(root)
    configs = sorted(list(root.rglob('data.yaml')) + list(root.rglob('dataset.yaml')) + list(root.rglob('data.yml')))
    for cfg in configs:
        try:
            parsed = yaml.safe_load(cfg.read_text(encoding='utf-8')) or {}
        except Exception:
            continue
        if isinstance(parsed, dict) and 'train' in parsed and 'val' in parsed and 'names' in parsed:
            return cfg
    return None


def _resolve_dir(value, base, yaml_parent):
    if isinstance(value, list):
        raise ValueError('This notebook adapter expects a single image directory per split; YAML lists are not supported yet.')
    path = Path(str(value))
    candidates = []
    if path.is_absolute():
        candidates.append(path)
    else:
        candidates.extend([Path(base) / path, Path(yaml_parent) / path, Path(yaml_parent) / path.name])
    for candidate in candidates:
        if candidate.exists() and candidate.is_dir():
            return candidate.resolve()
    raise FileNotFoundError(f'Cannot resolve split folder {value!r}. Checked: {candidates}')


def derive_label_dir(image_dir):
    image_dir = Path(image_dir)
    parts = list(image_dir.parts)
    indices = [i for i, part in enumerate(parts) if part.lower() == 'images']
    if indices:
        parts[indices[-1]] = 'labels'
        return Path(*parts)
    # Common split layout: dataset/<split>/images and dataset/<split>/labels.
    return image_dir.parent / 'labels'


def validate_yolo_label_file(path, class_count):
    path = Path(path)
    if not path.exists():
        return 0
    boxes = 0
    for line_no, line in enumerate(path.read_text(encoding='utf-8', errors='ignore').splitlines(), start=1):
        if not line.strip():
            continue
        parts = line.split()
        if len(parts) != 5:
            raise ValueError(f'{path}:{line_no} expected 5 fields, got {len(parts)}')
        try:
            class_id_float = float(parts[0])
            class_id = int(class_id_float)
            if class_id_float != class_id:
                raise ValueError('class id must be an integer')
            coords = [float(v) for v in parts[1:]]
        except ValueError as exc:
            raise ValueError(f'{path}:{line_no} contains non-numeric label data') from exc
        if class_id < 0 or class_id >= class_count:
            raise ValueError(f'{path}:{line_no} class id {class_id} outside 0..{class_count-1}')
        if not all(math.isfinite(v) and 0.0 <= v <= 1.0 for v in coords):
            raise ValueError(f'{path}:{line_no} coordinates must be finite and normalized to [0,1]')
        cx, cy, width, height = coords
        if width <= 0 or height <= 0:
            raise ValueError(f'{path}:{line_no} box width/height must be positive')
        if cx-width/2 < -1e-6 or cx+width/2 > 1.000001 or cy-height/2 < -1e-6 or cy+height/2 > 1.000001:
            raise ValueError(f'{path}:{line_no} box extends outside normalized image bounds')
        boxes += 1
    return boxes


def normalize_yolo_yaml(yaml_path, output_yaml, root_override=None):
    """Normalize YOLO paths to absolute paths and validate train/val/test labels."""
    yaml_path, output_yaml = Path(yaml_path), Path(output_yaml)
    cfg = yaml.safe_load(yaml_path.read_text(encoding='utf-8'))
    if not isinstance(cfg, dict):
        raise ValueError(f'Invalid YAML mapping: {yaml_path}')
    raw_names = cfg.get('names')
    if isinstance(raw_names, dict):
        try:
            keys = sorted(raw_names, key=lambda x:int(x))
        except (TypeError, ValueError):
            keys = sorted(raw_names, key=lambda x:str(x))
        names = [str(raw_names[k]) for k in keys]
    elif isinstance(raw_names, list):
        names = [str(x) for x in raw_names]
    else:
        raise ValueError(f'{yaml_path} must contain class names in names: list or mapping')
    if not names or len(set(names)) != len(names):
        raise ValueError('YOLO class names must be non-empty and unique')
    base = Path(root_override) if root_override else Path(cfg.get('path', yaml_path.parent))
    if not base.is_absolute():
        base = (yaml_path.parent / base).resolve()
    if not base.exists():
        base = yaml_path.parent.resolve()
    train_dir = _resolve_dir(cfg['train'], base, yaml_path.parent)
    val_dir = _resolve_dir(cfg['val'], base, yaml_path.parent)
    test_dir = None
    if cfg.get('test'):
        try:
            candidate_test_dir = _resolve_dir(cfg['test'], base, yaml_path.parent)
            if list_images(candidate_test_dir):
                test_dir = candidate_test_dir
            else:
                print('Warning: test split directory is empty; final test evaluation will be skipped.')
        except FileNotFoundError:
            print('Warning: test split path not found; final test evaluation will be skipped.')
    split_dirs = {'train':train_dir,'val':val_dir}
    if test_dir is not None:
        split_dirs['test'] = test_dir
    summary = {}
    for split, image_dir in split_dirs.items():
        images = list_images(image_dir)
        if not images:
            raise ValueError(f'{split} split has no images: {image_dir}')
        label_dir = derive_label_dir(image_dir)
        if split in ('train','val') and not label_dir.exists():
            raise FileNotFoundError(f'{split} label folder missing: {label_dir}')
        label_files = list(label_dir.rglob('*.txt')) if label_dir.exists() else []
        total_boxes = 0
        for label_file in label_files:
            total_boxes += validate_yolo_label_file(label_file, len(names))
        if split in ('train','val') and not label_files:
            raise ValueError(f'{split} has no label files under {label_dir}')
        if split in ('train','val') and total_boxes == 0:
            raise ValueError(f'{split} split contains no valid boxes')
        if split=='test' and not label_files:
            print(f'Warning: test labels are absent/empty at {label_dir}; test metrics may be invalid if defects exist.')
        summary[split] = {'images':len(images),'label_files':len(label_files),'boxes':total_boxes,'image_dir':str(image_dir),'label_dir':str(label_dir)}
    normalized = {'path':str(base.resolve()),'train':str(train_dir),'val':str(val_dir),'names':names}
    if test_dir is not None:
        normalized['test'] = str(test_dir)
    output_yaml.parent.mkdir(parents=True, exist_ok=True)
    output_yaml.write_text(yaml.safe_dump(normalized,sort_keys=False),encoding='utf-8')
    print('Normalized YOLO data YAML:',output_yaml)
    return output_yaml, names, summary


def convert_voc_xml_to_yolo(raw_root, output_root, seed=42, train_ratio=.70, val_ratio=.15):
    """Convert paired Pascal VOC XML/images into YOLO folders with a reproducible split."""
    raw_root, output_root = Path(raw_root), Path(output_root)
    xml_files = sorted(raw_root.rglob('*.xml'))
    image_files = list_images(raw_root)
    if not xml_files or not image_files:
        return None
    by_stem = defaultdict(list)
    for image in image_files:
        by_stem[image.stem.lower()].append(image)
    samples = []
    class_set = set()
    for xml in xml_files:
        matches = by_stem.get(xml.stem.lower(), [])
        if len(matches) != 1:
            continue
        try:
            import xml.etree.ElementTree as ET
            root = ET.parse(xml).getroot()
        except Exception:
            continue
        image = matches[0]
        with Image.open(image) as im:
            width, height = im.size
        boxes = []
        for obj in root.findall('object'):
            class_name = (obj.findtext('name') or '').strip()
            bnd = obj.find('bndbox')
            if not class_name or bnd is None:
                continue
            try:
                x1 = float(bnd.findtext('xmin')); y1 = float(bnd.findtext('ymin'))
                x2 = float(bnd.findtext('xmax')); y2 = float(bnd.findtext('ymax'))
            except (TypeError,ValueError):
                continue
            x1=max(0.0,min(x1,width-1)); y1=max(0.0,min(y1,height-1))
            x2=max(0.0,min(x2,width)); y2=max(0.0,min(y2,height))
            if x2<=x1 or y2<=y1:
                continue
            boxes.append((class_name,x1,y1,x2,y2))
            class_set.add(class_name)
        if boxes:
            samples.append({'image':image,'boxes':boxes,'width':width,'height':height})
    if len(samples)<30 or not class_set:
        return None
    if output_root.exists():
        shutil.rmtree(output_root)
    names=sorted(class_set)
    class_to_id={name:i for i,name in enumerate(names)}
    # Use dominant class to stratify when enough examples exist; otherwise fall back to seeded random.
    strata=[]
    for sample in samples:
        counts=Counter(b[0] for b in sample['boxes'])
        strata.append(counts.most_common(1)[0][0])
    try:
        from sklearn.model_selection import train_test_split
        indices=list(range(len(samples)))
        train_idx,remainder_idx=train_test_split(indices,test_size=(1-train_ratio),random_state=seed,stratify=strata)
        remainder_strata=[strata[i] for i in remainder_idx]
        test_fraction=val_ratio/(1-train_ratio)
        val_idx,test_idx=train_test_split(remainder_idx,test_size=test_fraction,random_state=seed,stratify=remainder_strata)
        train=[samples[i] for i in train_idx]; val=[samples[i] for i in val_idx]; test=[samples[i] for i in test_idx]
    except Exception:
        rng=random.Random(seed); shuffled=list(samples); rng.shuffle(shuffled)
        n_train=max(1,int(len(shuffled)*train_ratio)); n_val=max(1,int(len(shuffled)*val_ratio))
        train=shuffled[:n_train]; val=shuffled[n_train:n_train+n_val]; test=shuffled[n_train+n_val:]
        if not test: test=val[-1:]; val=val[:-1]
    for split,rows in [('train',train),('val',val),('test',test)]:
        (output_root/'images'/split).mkdir(parents=True,exist_ok=True)
        (output_root/'labels'/split).mkdir(parents=True,exist_ok=True)
        for idx,sample in enumerate(rows):
            image=sample['image']; name=f'{image.stem}_{idx:05d}'
            shutil.copy2(image,output_root/'images'/split/f'{name}{image.suffix.lower()}')
            lines=[]
            for class_name,x1,y1,x2,y2 in sample['boxes']:
                lines.append(f'{class_to_id[class_name]} {(x1+x2)/(2*sample["width"]):.6f} {(y1+y2)/(2*sample["height"]):.6f} {(x2-x1)/sample["width"]:.6f} {(y2-y1)/sample["height"]:.6f}')
            (output_root/'labels'/split/f'{name}.txt').write_text('\n'.join(lines),encoding='utf-8')
    yaml_path=output_root/'data.yaml'
    cfg={'path':str(output_root.resolve()),'train':'images/train','val':'images/val','test':'images/test','names':names}
    yaml_path.write_text(yaml.safe_dump(cfg,sort_keys=False),encoding='utf-8')
    return yaml_path


def yolo_label_path(image_path, image_dir, label_dir):
    image_path=Path(image_path); image_dir=Path(image_dir); label_dir=Path(label_dir)
    rel=image_path.relative_to(image_dir)
    return label_dir/rel.with_suffix('.txt')


def read_yolo_gt(image_path, image_dir, label_dir):
    image_path=Path(image_path)
    image=cv2.imread(str(image_path))
    if image is None:
        raise ValueError(f'Cannot read image: {image_path}')
    height,width=image.shape[:2]
    label_path=yolo_label_path(image_path,image_dir,label_dir)
    boxes=[]
    if label_path.exists():
        for line in label_path.read_text(encoding='utf-8',errors='ignore').splitlines():
            if not line.strip(): continue
            cls,cx,cy,bw,bh=map(float,line.split())
            boxes.append((int(cls),[max(0,(cx-bw/2)*width),max(0,(cy-bh/2)*height),min(width,(cx+bw/2)*width),min(height,(cy+bh/2)*height)]))
    return boxes,(width,height)


def box_iou_xyxy(a,b):
    x1=max(float(a[0]),float(b[0])); y1=max(float(a[1]),float(b[1]))
    x2=min(float(a[2]),float(b[2])); y2=min(float(a[3]),float(b[3]))
    inter=max(0,x2-x1)*max(0,y2-y1)
    area_a=max(0,float(a[2])-float(a[0]))*max(0,float(a[3])-float(a[1]))
    area_b=max(0,float(b[2])-float(b[0]))*max(0,float(b[3])-float(b[1]))
    return inter/max(area_a+area_b-inter,1e-9)


def cache_detection_predictions(model, image_paths, imgsz=640, device='cpu', low_conf=.01):
    """Cache predictions once at a low confidence; later confidence sweeps are cheap."""
    cache=[]
    for image_path in image_paths:
        result=model.predict(source=str(image_path),imgsz=imgsz,conf=low_conf,device=device,verbose=False)[0]
        pred=[]
        if result.boxes is not None and len(result.boxes):
            for box in result.boxes:
                pred.append((int(box.cls.item()),box.xyxy[0].detach().cpu().numpy().astype(float),float(box.conf.item())))
        cache.append({'image_path':Path(image_path),'predictions':pred})
    return cache


def detection_metrics_from_cache(cache, image_dir, label_dir, confidence=.25, iou_threshold=.5):
    totals={'tp':0,'fp':0,'fn':0,'images':0,'gt_boxes':0}
    for item in cache:
        image_path=item['image_path']
        gt,_=read_yolo_gt(image_path,image_dir,label_dir)
        preds=[p for p in item['predictions'] if p[2]>=confidence]
        preds=sorted(preds,key=lambda p:p[2],reverse=True)
        unmatched=set(range(len(gt)))
        tp=fp=0
        for cls,pbox,_score in preds:
            choices=[(idx,box_iou_xyxy(pbox,gbox)) for idx,(gcls,gbox) in enumerate(gt) if idx in unmatched and gcls==cls]
            best=max(choices,key=lambda x:x[1],default=None)
            if best is not None and best[1]>=iou_threshold:
                unmatched.remove(best[0]); tp+=1
            else:
                fp+=1
        fn=len(unmatched)
        totals['tp']+=tp; totals['fp']+=fp; totals['fn']+=fn; totals['images']+=1; totals['gt_boxes']+=len(gt)
    precision=totals['tp']/max(totals['tp']+totals['fp'],1)
    recall=totals['tp']/max(totals['tp']+totals['fn'],1)
    f1=2*precision*recall/max(precision+recall,1e-12)
    totals.update({'precision':precision,'recall':recall,'f1':f1,'confidence':confidence,'iou_threshold':iou_threshold})
    return totals


def confidence_sweep(cache, image_dir, label_dir, thresholds=None, target_recall=.95):
    if thresholds is None:
        thresholds=np.arange(.05,.51,.05).round(2).tolist()
    rows=[detection_metrics_from_cache(cache,image_dir,label_dir,float(t)) for t in thresholds]
    feasible=[r for r in rows if r['recall']>=target_recall]
    chosen=max(feasible,key=lambda r:r['precision']) if feasible else max(rows,key=lambda r:r['f1'])
    return rows,chosen


def perturb_image(image, kind, seed=42):
    image=np.asarray(image).copy()
    if kind=='original': return image
    if kind=='dark': return cv2.convertScaleAbs(image,alpha=.65,beta=0)
    if kind=='bright': return cv2.convertScaleAbs(image,alpha=1.15,beta=20)
    if kind=='contrast_low': return cv2.convertScaleAbs(image,alpha=.75,beta=35)
    if kind=='blur': return cv2.GaussianBlur(image,(5,5),1.4)
    if kind=='noise':
        rng=np.random.default_rng(seed)
        return np.clip(image.astype(np.float32)+rng.normal(0,12,image.shape),0,255).astype(np.uint8)
    raise KeyError(f'Unknown perturbation: {kind}')


def image_quality_stats(image):
    image=np.asarray(image)
    if image.ndim==2:
        gray=image
    else:
        gray=cv2.cvtColor(image,cv2.COLOR_BGR2GRAY)
    return {'brightness':float(gray.mean()),'contrast':float(gray.std()),'sharpness':float(cv2.Laplacian(gray,cv2.CV_64F).var())}


def tiled_predict(model, image_bgr, class_names=None, tile_size=640, overlap=.20, imgsz=640, conf=.25, device='cpu'):
    """Tile large images, offset boxes, and merge with class-wise greedy NMS."""
    if tile_size<=0 or not (0.0<=overlap<1.0):
        raise ValueError('tile_size must be positive and overlap must be in [0,1)')
    h,w=image_bgr.shape[:2]
    if h<=tile_size and w<=tile_size:
        results=model.predict(source=image_bgr,imgsz=imgsz,conf=conf,device=device,verbose=False)[0]
        detections=[]
        for b in results.boxes:
            cls=int(b.cls.item()); box=b.xyxy[0].cpu().numpy().astype(float)
            detections.append({'class_id':cls,'class_name':(class_names or model.names)[cls],'confidence':float(b.conf.item()),'bbox_xyxy':box.tolist()})
        return detections
    step=max(1,int(tile_size*(1-overlap)))
    def starts(length):
        if length<=tile_size: return [0]
        s=list(range(0,length-tile_size+1,step))
        if not s or s[-1]!=length-tile_size: s.append(length-tile_size)
        return s
    candidates=[]
    for y in starts(h):
        for x in starts(w):
            tile=image_bgr[y:min(y+tile_size,h),x:min(x+tile_size,w)]
            result=model.predict(source=tile,imgsz=imgsz,conf=conf,device=device,verbose=False)[0]
            for b in result.boxes:
                cls=int(b.cls.item()); box=b.xyxy[0].cpu().numpy().astype(float)
                box[[0,2]]+=x; box[[1,3]]+=y
                candidates.append({'class_id':cls,'class_name':(class_names or model.names)[cls],'confidence':float(b.conf.item()),'bbox_xyxy':box.tolist()})
    # Greedy, class-specific NMS to remove duplicates across overlapping tiles.
    kept=[]
    for cls_id in sorted(set(d['class_id'] for d in candidates)):
        group=sorted((d for d in candidates if d['class_id']==cls_id),key=lambda d:d['confidence'],reverse=True)
        while group:
            best=group.pop(0); kept.append(best)
            group=[d for d in group if box_iou_xyxy(best['bbox_xyxy'],d['bbox_xyxy'])<.50]
    return kept
