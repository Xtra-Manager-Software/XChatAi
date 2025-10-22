# --- Membersihkan Proyek (Langkah Awal) ---
Write-Host "Membersihkan cache build..."
Remove-Item -Path "app\build" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "build" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path ".gradle" -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "Membersihkan cache Gradle..."
.\gradlew.bat clean
Write-Host "Pembersihan selesai."
Write-Host ""

# --- Meminta Informasi Keystore Secara Interaktif ---
Write-Host "Silakan masukkan detail keystore untuk menandatangani aplikasi:"

# 1. Meminta path keystore
$KEYSTORE_PATH = Read-Host "Masukkan path ke file keystore Anda"

# Validasi sederhana untuk memeriksa apakah file ada
if (-not (Test-Path $KEYSTORE_PATH -PathType Leaf)) {
    Write-Host "Error: File keystore tidak ditemukan di '$KEYSTORE_PATH'"
    exit 1
}

# 2. Meminta alias key
$KEY_ALIAS = Read-Host "Masukkan alias key Anda"

# 3. Meminta password keystore (input disembunyikan)
$secureKeystorePass = Read-Host "Masukkan password keystore Anda" -AsSecureString
$KEYSTORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKeystorePass)
)

# 4. Meminta password key alias (input disembunyikan)
$secureKeyPass = Read-Host "Masukkan password untuk alias '$KEY_ALIAS'" -AsSecureString
$KEY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKeyPass)
)

Write-Host "`nMasukkan changelog untuk rilis ini (tekan Enter dua kali setelah selesai):"
$CHANGELOG = @()
do {
    $line = Read-Host
    if ($line -ne "") {
        $CHANGELOG += $line
    }
} while ($line -ne "")
$CHANGELOG = $CHANGELOG -join "`n"

# --- Menjalankan Build Gradle dengan Properti ---
Write-Host "Memulai build release dengan informasi keystore dan Changelog yang diberikan..."

# Menjalankan gradlew dengan meneruskan variabel sebagai properti
$gradleCommand = ".\gradlew.bat buildAndPublish " +
    "-PmyKeystorePath=`"$KEYSTORE_PATH`" " +
    "-PmyKeystorePassword=`"$KEYSTORE_PASSWORD`" " +
    "-PmyKeyAlias=`"$KEY_ALIAS`" " +
    "-PmyKeyPassword=`"$KEY_PASSWORD`" " +
    "-PmyChangelog=`"$CHANGELOG`""

Invoke-Expression $gradleCommand

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Build release selesai!"
    Write-Host "Anda bisa menemukan APK di folder: app/build/outputs/apk/release/"
} else {
    Write-Host "❌ Build gagal. Silakan periksa log di atas."
    exit 1
}
