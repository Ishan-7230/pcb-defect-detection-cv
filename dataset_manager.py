#!/usr/bin/env python3
"""Dataset manager for the Industry 4.0 offline visual quality inspector.

Preserves the user's dataset registry while providing safer download, image-folder
organization, dataset inspection, and MVTec-style anomaly loading utilities.
"""
from __future__ import annotations

import argparse
import random
import shutil
import subprocess
import zipfile
from collections import Counter
from pathlib import Path
from typing import Optional


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff", ".webp"}
DATASETS = {
    "neu": {"source": "kaggle", "id": "kaustubhdikshit/neu-surface-defect-database", "desc": "NEU steel surface defect dataset; commonly used for image-level classification, 1,800 images."},
    "pcb": {"source": "kaggle", "id": "norbertelter/pcb-defect-dataset", "desc": "PCB defect dataset (annotation format must be checked before use)."},
    "pcb_small": {"source": "kaggle", "id": "akhatova/pcb-defects", "desc": "Small PCB defect dataset (annotation format must be checked before use)."},
    "magnetic_tile": {"source": "kaggle", "id": "alex000kim/magnetic-tile-surface-defects", "desc": "Magnetic tile surface defects; usually used for classification/anomaly experiments."},
    "gc10": {"source": "kaggle", "id": "alex000kim/gc10det", "desc": "GC10-DET steel defect detection dataset."},
    "gc10_hf": {"source": "hf", "id": "dronefreak/GC10-DET", "desc": "GC10-DET steel defect detection dataset with YOLO-style files and splits."},
    "severstal_hf": {"source": "hf", "id": "Voxel51/severstal_steel_defects", "desc": "Severstal steel defect dataset; segmentation task, not automatically converted to boxes."},
    "mvtec_hf": {"source": "hf", "id": "Voxel51/mvtec-ad", "desc": "MVTec AD mirror; verify layout and licensing."},
    "adnet": {"source": "hf", "id": "linglingling009/ADNet", "desc": "Large anomaly dataset; download only when storage is sufficient."},
}
HUGE_DATASETS = {"adnet"}
MANUAL_DATASETS = {
    "MVTec AD official": "https://www.mvtec.com/company/research/datasets/mvtec-ad",
    "MVTec AD 2": "https://www.mvtec.com/company/research/datasets/mvtec-ad-2",
    "MVTec LOCO AD": "https://www.mvtec.com/company/research/datasets/mvtec-loco-ad",
    "MVTec 3D-AD": "https://www.mvtec.com/company/research/datasets/mvtec-3d-ad",
    "VisA AWS Open Data": "https://registry.opendata.aws/visa/",
    "KolektorSDD2": "https://www.vicos.si/resources/kolektorsdd2/",
    "Real-IAD": "https://realiad4ad.github.io/Real-IAD/",
    "Kaputt": "https://www.kaputt-dataset.com/",
    "NEU official": "https://faculty.neu.edu.cn/songkc/en/zdylm/263265/",
}


def is_image(path: Path) -> bool:
    return Path(path).suffix.lower() in IMAGE_EXTENSIONS



def list_datasets() -> None:
    print("Registered datasets:")
    for name, cfg in DATASETS.items():
        tag = " [LARGE]" if name in HUGE_DATASETS else ""
        print(f"\n{name}{tag}\n  source: {cfg['source']}\n  id: {cfg['id']}\n  description: {cfg['desc']}")


def print_manual_datasets() -> None:
    print("Manual/official dataset links:")
    for name, url in MANUAL_DATASETS.items():
        print(f"{name}: {url}")


def _has_images(path: Path) -> bool:
    return path.exists() and any(is_image(p) for p in path.rglob("*"))


def _copy_tree_contents(source: Path, destination: Path) -> None:
    source = Path(source)
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    if source.resolve() == destination.resolve():
        return
    for item in source.iterdir():
        target = destination / item.name
        if item.is_dir():
            shutil.copytree(item, target, dirs_exist_ok=True)
        else:
            shutil.copy2(item, target)


def download_dataset(name: str, root: Path = Path("data/raw"), force: bool = False) -> Path:
    """Download a registered dataset. Returns the destination directory.


    Kaggle uses kagglehub first and falls back to the Kaggle CLI. Hugging Face
    datasets use snapshot_download. Network and account access may be required.
    """
    if name not in DATASETS:
        raise KeyError(f"Unknown dataset {name!r}. Valid names: {sorted(DATASETS)}")
    destination = Path(root) / name
    destination.mkdir(parents=True, exist_ok=True)
    if _has_images(destination) and not force:
        print(f"Using existing images in: {destination}")
        return destination
    cfg = DATASETS[name]
    errors = []
    if cfg["source"] == "kaggle":
        try:
            import kagglehub
            downloaded = Path(kagglehub.dataset_download(cfg["id"]))
            _copy_tree_contents(downloaded, destination)
        except Exception as exc:
            errors.append(f"kagglehub: {exc}")
            try:
                cmd = ["kaggle", "datasets", "download", "-d", cfg["id"], "-p", str(destination), "--unzip"]
                subprocess.run(cmd, check=True, capture_output=True, text=True)
            except Exception as cli_exc:
                errors.append(f"Kaggle CLI: {cli_exc}")
    elif cfg["source"] == "hf":
        try:
            from huggingface_hub import snapshot_download
            snapshot_download(repo_id=cfg["id"], repo_type="dataset", local_dir=str(destination))
        except Exception as exc:
            errors.append(f"Hugging Face: {exc}")
    if not _has_images(destination):
        raise RuntimeError(
            f"Download failed or produced no image files for {name}.\n"
            f"Details: {' | '.join(errors) or 'No download result'}\n"
            "If Kaggle requires authentication, add kaggle.json in Colab or download manually."
        )
    print(f"Dataset ready at: {destination}")
    return destination



def summarize_folder(source: Path) -> dict:
    source = Path(source)
    if not source.exists():
        raise FileNotFoundError(source)
    counts = Counter()
    for image in source.rglob("*"):
        if image.is_file() and is_image(image):
            relative = image.relative_to(source)
            class_or_split = str(relative.parent) if len(relative.parts) > 1 else "root"
            counts[class_or_split] += 1
    print(f"Image summary: {source}")
    for name, count in sorted(counts.items()):
        print(f"{count:6d}  {name}")
    print(f"TOTAL: {sum(counts.values())}")
    return dict(counts)


def organize_classification_dataset(
    source: Path,
    destination: Path,
    seed: int = 42,
    train_ratio: float = 0.70,
    val_ratio: float = 0.15,
    clear_destination: bool = True,
) -> Path:
    """Create class-folder train/val/test splits from a flat class-folder source.

    Every class needs at least 3 images so all three splits can contain examples.
    This is a random image split; batch/group splitting is preferable for factory data.
    """
    source, destination = Path(source), Path(destination)
    if not source.exists():
        raise FileNotFoundError(source)
    if not (0 < train_ratio < 1 and 0 < val_ratio < 1 and train_ratio + val_ratio < 1):
        raise ValueError("train_ratio and val_ratio must be positive and sum to less than 1")
    class_dirs = sorted(
        d for d in source.iterdir() if d.is_dir() and any(is_image(f) for f in d.iterdir() if f.is_file())
    )
    if len(class_dirs) < 2:
        raise ValueError(f"Expected at least two class folders directly under {source}")
    if clear_destination and destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True, exist_ok=True)
    rng = random.Random(seed)
    test_ratio = 1.0 - train_ratio - val_ratio
    for class_dir in class_dirs:
        files = sorted(p for p in class_dir.iterdir() if p.is_file() and is_image(p))
        if len(files) < 3:
            raise ValueError(f"Class {class_dir.name} has {len(files)} images; need at least 3 for train/val/test")
        rng.shuffle(files)
        n_train = max(1, int(round(len(files) * train_ratio)))
        n_val = max(1, int(round(len(files) * val_ratio)))
        n_test = len(files) - n_train - n_val
        if n_test < 1:
            n_test = 1
            if n_train > 1:
                n_train -= 1
            else:
                n_val -= 1
        splits = {"train": files[:n_train], "val": files[n_train:n_train+n_val], "test": files[n_train+n_val:]}
        for split, split_files in splits.items():
            target_dir = destination / split / class_dir.name
            target_dir.mkdir(parents=True, exist_ok=True)
            for index, path in enumerate(split_files):
                shutil.copy2(path, target_dir / f"{index:06d}_{path.name}")
    print(f"Prepared class-folder split: {destination}")
    return destination



# Optional PyTorch/torchvision imports for MVTec-style data.
try:
    import torch
    from torch.utils.data import Dataset, DataLoader
    from torchvision import transforms
    from PIL import Image
    TORCH_AVAILABLE = True
except Exception:
    TORCH_AVAILABLE = False


if TORCH_AVAILABLE:
    class MVTecADDataset(Dataset):
        """MVTec AD loader: train uses only good; test uses good and defect categories."""
        def __init__(self, root: str | Path, category: str, split: str = "train", image_size: int = 256):
            self.root = Path(root) / category
            self.split = split
            if split not in {"train", "test"}:
                raise ValueError("split must be 'train' or 'test'")
            if not self.root.exists():
                raise FileNotFoundError(self.root)
            self.transform = transforms.Compose([
                transforms.Resize((image_size, image_size)),
                transforms.ToTensor(),
                transforms.Normalize(mean=(0.485, 0.456, 0.406), std=(0.229, 0.224, 0.225)),
            ])
            self.mask_transform = transforms.Compose([
                transforms.Resize((image_size, image_size), interpolation=transforms.InterpolationMode.NEAREST),
                transforms.ToTensor(),
            ])
            self.samples = []
            self._collect_samples()

        def _find_mask(self, defect_name: str, image_path: Path) -> Optional[Path]:
            mask_dir = self.root / "ground_truth" / defect_name
            if not mask_dir.exists():
                return None
            candidates = [mask_dir / f"{image_path.stem}_mask.png", mask_dir / image_path.name]
            candidates += [mask_dir / f"{image_path.stem}_mask{ext}" for ext in IMAGE_EXTENSIONS]
            candidates += [mask_dir / f"{image_path.stem}{ext}" for ext in IMAGE_EXTENSIONS]
            return next((p for p in candidates if p.exists()), None)

        def _collect_samples(self):
            if self.split == "train":
                good_dir = self.root / "train" / "good"
                if not good_dir.exists():
                    raise FileNotFoundError(f"Expected MVTec training normals: {good_dir}")
                for p in sorted(good_dir.iterdir()):
                    if p.is_file() and is_image(p):
                        self.samples.append((p, None, 0, "good"))
            else:
                test_dir = self.root / "test"
                if not test_dir.exists():
                    raise FileNotFoundError(test_dir)
                for defect_dir in sorted(test_dir.iterdir()):
                    if not defect_dir.is_dir():
                        continue
                    defect_name = defect_dir.name
                    label = 0 if defect_name.lower() == "good" else 1
                    for p in sorted(defect_dir.iterdir()):
                        if p.is_file() and is_image(p):
                            mask = self._find_mask(defect_name, p) if label else None
                            self.samples.append((p, mask, label, defect_name))

        def __len__(self):
            return len(self.samples)

        def __getitem__(self, index):
            image_path, mask_path, label, defect_name = self.samples[index]
            image = Image.open(image_path).convert("RGB")
            image_tensor = self.transform(image)
            if mask_path is not None:
                mask_image = Image.open(mask_path).convert("L")
                mask_tensor = (self.mask_transform(mask_image) > 0).float()
            else:
                mask_tensor = torch.zeros((1, image_tensor.shape[-2], image_tensor.shape[-1]), dtype=torch.float32)
            return {
                "image": image_tensor,
                "mask": mask_tensor,
                "label": int(label),
                "defect_type": defect_name,
                "image_path": str(image_path),
            }

    def make_anomaly_dataloaders(root, category, batch_size=8, image_size=256, num_workers=0):
        train_ds = MVTecADDataset(root, category, "train", image_size)
        test_ds = MVTecADDataset(root, category, "test", image_size)
        return (
            DataLoader(train_ds, batch_size=batch_size, shuffle=True, num_workers=num_workers),
            DataLoader(test_ds, batch_size=batch_size, shuffle=False, num_workers=num_workers),
        )
else:
    MVTecADDataset = None
    make_anomaly_dataloaders = None




def demo_anomaly_dataset(root: Path, category: str, image_size: int, batch_size: int) -> None:
    if not TORCH_AVAILABLE:
        raise RuntimeError("PyTorch/torchvision are required for the MVTec demo loader.")
    train_loader, test_loader = make_anomaly_dataloaders(root, category, batch_size, image_size, num_workers=0)
    print(f"MVTec category={category} | train batches={len(train_loader)} | test batches={len(test_loader)}")
    if len(train_loader):
        batch=next(iter(train_loader))
        print("Train batch:", batch["image"].shape, "mask:", batch["mask"].shape)
    if len(test_loader):
        batch=next(iter(test_loader))
        print("Test batch:", batch["image"].shape, "labels:", batch["label"], "types:", batch["defect_type"])

def main():
    parser = argparse.ArgumentParser(description="Manufacturing defect dataset manager")
    sub = parser.add_subparsers(dest="command")
    sub.add_parser("list", help="List registered datasets")
    sub.add_parser("manual", help="Show official/manual dataset links")
    d = sub.add_parser("download", help="Download one or more datasets")
    d.add_argument("names", nargs="+")
    d.add_argument("--root", default="data/raw")
    d.add_argument("--force", action="store_true")
    d.add_argument("--yes", action="store_true", help="Required for large dataset ADNet")
    s = sub.add_parser("summarize", help="Summarize images under a folder")
    s.add_argument("--source", required=True)
    o = sub.add_parser("organize-classification", help="Create train/val/test class folders")
    o.add_argument("--source", required=True)
    o.add_argument("--dest", required=True)
    o.add_argument("--seed", type=int, default=42)
    o.add_argument("--train-ratio", type=float, default=.70)
    o.add_argument("--val-ratio", type=float, default=.15)
    anomaly_parser = sub.add_parser("demo-anomaly", help="Smoke-test an MVTec-style folder")
    anomaly_parser.add_argument("--root", default="data/raw/mvtec")
    anomaly_parser.add_argument("--category", default="hazelnut")
    anomaly_parser.add_argument("--image-size", type=int, default=256)
    anomaly_parser.add_argument("--batch-size", type=int, default=4)
    args = parser.parse_args()
    if args.command == "list":
        list_datasets()
    elif args.command == "manual":
        print_manual_datasets()
    elif args.command == "download":
        for name in args.names:
            if name in HUGE_DATASETS and not args.yes:
                print(f"Skipping {name}; add --yes to confirm large download.")
            else:
                download_dataset(name, Path(args.root), args.force)
    elif args.command == "summarize":
        summarize_folder(Path(args.source))
    elif args.command == "organize-classification":
        organize_classification_dataset(Path(args.source), Path(args.dest), args.seed, args.train_ratio, args.val_ratio)
    elif args.command == "demo-anomaly":
        demo_anomaly_dataset(Path(args.root), args.category, args.image_size, args.batch_size)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
