param([string]$In, [string]$Out)
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile($In)
$W = 1920; $H = 1080
$scale = [math]::Max($W / $src.Width, $H / $src.Height)
$cw = $W / $scale; $ch = $H / $src.Height * $src.Height / $scale
$cw = [int]($W / $scale); $ch = [int]($H / $scale)
$cx = [int](($src.Width - $cw) / 2)
$cy = [int](($src.Height - $ch) * 0.55)
$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = 'HighQualityBicubic'
$g.SmoothingMode = 'HighQuality'
$g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, $W, $H), (New-Object System.Drawing.Rectangle $cx, $cy, $cw, $ch), [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
$p = New-Object System.Drawing.Imaging.EncoderParameters 1
$p.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), ([long]85)
$bmp.Save($Out, $codec, $p)
$bmp.Dispose(); $src.Dispose()
"{0}x{1} from {2}x{3} crop {4},{5} {6}x{7} -> {8:N0} KB" -f $W, $H, $src.Width, $src.Height, $cx, $cy, $cw, $ch, ((Get-Item $Out).Length / 1KB)
