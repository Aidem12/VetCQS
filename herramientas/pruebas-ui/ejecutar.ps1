param([string]$RepositorioMaven = "$env:USERPROFILE\.m2\repository")
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$salida = Join-Path $PSScriptRoot 'target'
$jdk = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac.exe).Source }
New-Item -ItemType Directory -Path $salida -Force | Out-Null
foreach ($modulo in @('cliente-portal', 'cliente-caja', 'cliente-consultorio')) {
    & mvn -q -f (Join-Path $raiz "$modulo/pom.xml") compile
    if ($LASTEXITCODE -ne 0) { throw "No se pudo compilar $modulo" }
}
$fx = (Get-ChildItem (Join-Path $RepositorioMaven 'org/openjfx') -Recurse -Filter '*21.0.5-win.jar').FullName -join ';'
$jackson = (Get-ChildItem (Join-Path $RepositorioMaven 'com/fasterxml/jackson/core') -Recurse -Filter '*2.17.3.jar').FullName
$clases = @('cliente-portal','cliente-caja','cliente-consultorio') | ForEach-Object { Join-Path $raiz "$_/target/classes" }
$cp = (@($jackson) + @($clases) + @($salida)) -join ';'
& "$jdk/javac.exe" -encoding UTF-8 --module-path $fx --add-modules javafx.controls,jdk.httpserver -cp $cp -d $salida (Join-Path $PSScriptRoot 'FlujoClinicoTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Error al compilar la prueba UI' }
& "$jdk/java.exe" --module-path $fx --add-modules javafx.controls,jdk.httpserver -cp $cp FlujoClinicoTest
if ($LASTEXITCODE -ne 0) { throw 'Falló el recorrido UI' }
