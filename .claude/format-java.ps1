$inputJson = $input | Out-String | ConvertFrom-Json
$filePath = $inputJson.tool_input.file_path

if (-not $filePath -or $filePath -notmatch '\.java$') { exit 0 }
if (-not (Test-Path $filePath)) { exit 0 }

$jar = "d:\AI\chat\.claude\tools\google-java-format.jar"
$flags = @(
    "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
)

& java @flags -jar $jar --replace $filePath 2>$null
exit 0
