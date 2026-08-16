param([string]$docPath)
$docPath = (Resolve-Path $docPath).Path
$pdfPath = [System.IO.Path]::ChangeExtension($docPath, "pdf")
$word = New-Object -ComObject Word.Application
$word.Visible = $false
try {
    $doc = $word.Documents.Open($docPath)
    $doc.SaveAs([ref]$pdfPath, [ref]17)  # wdFormatPDF = 17
    $doc.Close()
    Write-Output "OK: $pdfPath"
} finally {
    $word.Quit()
}
