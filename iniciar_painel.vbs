' Script VBScript para inicializar o Robocode Torneio Launcher sem janela de console preta
' Util quando a execucao de arquivos .bat eh bloqueada por politicas de seguranca do Windows.
Set objFSO = CreateObject("Scripting.FileSystemObject")
Set objShell = CreateObject("WScript.Shell")

strCurrentDir = objFSO.GetParentFolderName(WScript.ScriptFullName)
objShell.CurrentDirectory = strCurrentDir

strCmd = "javaw.exe -cp ""libs\*;.;torneio"" -Xmx512M -XX:+IgnoreUnrecognizedVMOptions " & _
         """--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED"" " & _
         """--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"" " & _
         """--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED"" " & _
         """--add-opens=java.desktop/sun.awt=ALL-UNNAMED"" " & _
         "torneio.TorneioMain"

intReturn = objShell.Run(strCmd, 1, False)

If intReturn <> 0 Then
    ' Se falhar com javaw, tenta com java normal exibindo o console
    strFallbackCmd = "cmd.exe /k cd /d """ & strCurrentDir & """ && java -cp ""libs\*;.;torneio"" -Xmx512M torneio.TorneioMain"
    objShell.Run strFallbackCmd, 1, False
End If
