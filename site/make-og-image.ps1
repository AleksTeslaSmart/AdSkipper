# Draws site/og-image.png (1200x630): the link-preview picture for WhatsApp, Telegram and others.
# Run once with Windows PowerShell:  powershell -ExecutionPolicy Bypass -File site\make-og-image.ps1
Add-Type -AssemblyName System.Drawing

$W = 1200; $H = 630
$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.TextRenderingHint = 'AntiAliasGridFit'

$bg     = [System.Drawing.Color]::FromArgb(14, 16, 22)
$ink    = [System.Drawing.Color]::FromArgb(236, 238, 244)
$muted  = [System.Drawing.Color]::FromArgb(154, 160, 179)
$accent = [System.Drawing.Color]::FromArgb(255, 106, 51)
$g.Clear($bg)

function RoundRect($x, $y, $w, $h, $r) {
  $p = New-Object System.Drawing.Drawing2D.GraphicsPath
  $p.AddArc($x, $y, 2*$r, 2*$r, 180, 90)
  $p.AddArc($x + $w - 2*$r, $y, 2*$r, 2*$r, 270, 90)
  $p.AddArc($x + $w - 2*$r, $y + $h - 2*$r, 2*$r, 2*$r, 0, 90)
  $p.AddArc($x, $y + $h - 2*$r, 2*$r, 2*$r, 90, 90)
  $p.CloseFigure(); return $p
}

# Logo: orange rounded square with a fast-forward mark
$g.FillPath((New-Object System.Drawing.SolidBrush $accent), (RoundRect 90 150 150 150 34))
$dark = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(20, 16, 14))
$tri1 = [System.Drawing.PointF[]]@((New-Object System.Drawing.PointF 122,185), (New-Object System.Drawing.PointF 168,225), (New-Object System.Drawing.PointF 122,265))
$tri2 = [System.Drawing.PointF[]]@((New-Object System.Drawing.PointF 170,185), (New-Object System.Drawing.PointF 216,225), (New-Object System.Drawing.PointF 170,265))
$g.FillPolygon($dark, $tri1); $g.FillPolygon($dark, $tri2)

# Name and taglines
$fName = New-Object System.Drawing.Font 'Segoe UI', 88, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$fTag  = New-Object System.Drawing.Font 'Segoe UI', 44, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$fSub  = New-Object System.Drawing.Font 'Segoe UI', 32, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
$g.DrawString('AdSkipper', $fName, (New-Object System.Drawing.SolidBrush $ink), 275, 160)
$g.DrawString('YouTube ads skip themselves', $fTag, (New-Object System.Drawing.SolidBrush $accent), 92, 360)
$g.DrawString('Реклама в YouTube пропускается сама', $fSub, (New-Object System.Drawing.SolidBrush $ink), 94, 425)
$g.DrawString('Free Android app · open source · 11 languages', $fSub, (New-Object System.Drawing.SolidBrush $muted), 94, 475)

# Bottom accent bar
$g.FillRectangle((New-Object System.Drawing.SolidBrush $accent), 0, ($H - 12), $W, 12)

$out = Join-Path $PSScriptRoot 'og-image.png'
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
"saved $out"
