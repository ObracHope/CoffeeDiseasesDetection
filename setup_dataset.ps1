powershell
$classes = @("Healthy", "Rust", "BerryDisease", "Wilt", "LeafMiner", "RootRot", "IsNotCoffee")
$base = "c:\Users\Obrac\AndroidStudioProjects\CoffeeDiseasesDetection"

foreach ($c in $classes) {
    New-Item -ItemType Directory -Force -Path "$base\dataset\train\$c"
    New-Item -ItemType Directory -Force -Path "$base\dataset\validation\$c"
}

Write-Host "Folda za Dataset zimetengenezwa. Tafadhali weka picha halisi ndani ya kila folda kabla ya kuanza training!" -ForegroundColor Green