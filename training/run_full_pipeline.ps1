# Full pipeline: download web images -> prepare split -> train -> copy model to Android assets
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Dataset = Join-Path $Root "dataset"
$Train = Join-Path $Dataset "train"
$Out = Join-Path $Root "training\output"
$Assets = Join-Path $Root "app\src\main\assets\coffee_disease_model.tflite"

$Python = "py -3.11"

Write-Host "=== 1/5 Download extra web images (optional) ===" -ForegroundColor Cyan
try {
    Invoke-Expression "$Python `"$Root\training\download_web_images.py`" --dataset-train-dir `"$Train`" --not-coffee-count 80 --wilt-count 40 --root-rot-count 40 --berry-count 40"
} catch {
    Write-Host "Web download skipped or partial (rate limit OK)" -ForegroundColor Yellow
}

Write-Host "=== 2/5 Augment weak classes (synthetic + color aug) ===" -ForegroundColor Cyan
Invoke-Expression "$Python `"$Root\training\augment_weak_classes.py`" --dataset-train-dir `"$Train`" --synthetic-per-class 120"

Write-Host "=== 3/6 Prepare train/validation split ===" -ForegroundColor Cyan
Invoke-Expression "$Python `"$Root\training\prepare_dataset.py`" --dataset-root `"$Dataset`""

Write-Host "=== 4/6 Clean invalid images ===" -ForegroundColor Cyan
Invoke-Expression "$Python `"$Root\training\clean_dataset.py`" --dataset-root `"$Dataset`""

Write-Host "=== 5/7 Train coffee gate (Coffee vs NotCoffee) ===" -ForegroundColor Cyan
Invoke-Expression "$Python `"$Root\training\train_coffee_gate.py`" --dataset-root `"$Dataset`" --output-dir `"$Out`" --epochs 10 --batch-size 32"

Write-Host "=== 6/7 Train disease TFLite model ===" -ForegroundColor Cyan
Invoke-Expression "$Python `"$Root\training\train_multiclass_tflite.py`" --dataset-dir `"$Dataset`" --output-dir `"$Out`" --epochs 20 --batch-size 16"

Write-Host "=== 7/7 Copy models to app assets ===" -ForegroundColor Cyan
$Model = Join-Path $Out "coffee_disease_model.tflite"
$Gate = Join-Path $Out "coffee_gate.tflite"
if (-not (Test-Path $Model)) { throw "Model not found: $Model" }
if (-not (Test-Path $Gate)) { throw "Gate model not found: $Gate" }
New-Item -ItemType Directory -Force -Path (Split-Path $Assets) | Out-Null
Copy-Item -Force $Model (Join-Path (Split-Path $Assets) "coffee_disease_model.tflite")
Copy-Item -Force $Gate (Join-Path (Split-Path $Assets) "coffee_gate.tflite")
Write-Host "Done. Models copied to assets." -ForegroundColor Green
