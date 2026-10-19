' Script para criar automaticamente um Atalho oficial na Area de Trabalho do Windows (.lnk)
' Como eh um Atalho (.lnk) que chama o javaw.exe diretamente, ele ignora politicas que bloqueiam .bat.

Set WshShell = CreateObject("WScript.Shell")
strDesktop = WshShell.SpecialFolders("Desktop")
Set objFSO = CreateObject("Scripting.FileSystemObject")
strCurrentDir = objFSO.GetParentFolderName(WScript.ScriptFullName)

strShortcutPath = strDesktop & "\Robocode Torneio.lnk"
Set oLink = WshShell.CreateShortcut(strShortcutPath)

oLink.TargetPath = "javaw.exe"
oLink.Arguments = "-cp ""libs\*;.;torneio"" -Xmx512M -XX:+IgnoreUnrecognizedVMOptions ""--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED"" ""--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"" ""--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED"" ""--add-opens=java.desktop/sun.awt=ALL-UNNAMED"" torneio.TorneioMain"
oLink.WorkingDirectory = strCurrentDir
oLink.WindowStyle = 1

If objFSO.FileExists(strCurrentDir & "\robocode.ico") Then
    oLink.IconLocation = strCurrentDir & "\robocode.ico, 0"
End If

oLink.Description = "Painel Oficial do Robocode Torneio"
oLink.Save

MsgBox "Atalho 'Robocode Torneio' criado com sucesso na sua Area de Trabalho!" & vbCrLf & vbCrLf & _
       "Agora voce pode abrir o painel diretamente pelo icone na Area de Trabalho sem precisar de arquivo .bat.", _
       vbInformation, "Robocode Torneio"
