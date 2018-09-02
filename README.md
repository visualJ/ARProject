# Positionierung und Verdeckung von AR-Elementen

Diese Implementierung auf Basis des CloudAnchor Beispielcodes von Google zeigt,
wie virtuelle Objekte persistent positioniert und von der Umgebung verdeckt werden können.

## Einrichtung

 Es gelten die gleichen Einrichtungsschritte, wie für das CloudAnchor Beispiel, siehe [Get started with Cloud Anchors for Android](https://developers.google.com/ar/develop/java/cloud-anchors/cloud-anchors-quickstart-android).
 Insbesondere muss in Firebase ein Projekt mit Datenbank und CloudAnkor API Zugriff eingerichtet werden.
 Dort muss dann auch die google-services.json erhalten und in das Projekt eingefügt werden.
 Das Gerät, auf dem die App ausgeführt wird muss natürlich auch ARCore unterstützen und installiert haben.
 
 ## Verwendung
 Im Folgenden wird grob die Verwendung der App beschrieben.
 
 ### Ankerpunkt setzen
 Der Host Button kann verwendet werden, um einen Ankerpunkt zu setzen. Dazu sollte zunächst die Umgebung gut gescannt werden,
 sodass ARCore Features in der Umgebung erkennen kann. Ankerpunkte können nur auf erkannten Flächen gesetzt werden.
 Dazu muss einfach auf eine Fläche getippt werden, um einen Ankerpunkt zu setzen. Danach muss gewartet werden, bis am unteren Bildschirmrand angezeigt wird, dass das Setzen erfolgreich war.
 
 ### Referenzpunkte und Stereobilder
 Wenn ein Ankerpunkt aktiv ist (gesetzt oder erkannt wurde), können Referenzpunkte angelegt werden.
 Dazu wird das Gerät an eine Stelle bewegt, wo ein Referenzpunkt angelegt werden soll.
 Dann wird das Gerät langsam und gleichmäßig auf einer geraden Linie ohne Rotation von links nach rechts (Richtung ist wichtig!) bewegt.
 Wenn man dann ein einer gleicnmäßigen Bewegung ist, löst ein Tippen auf den Save Image Button den
 Speichervorgang aus (Man kann prinzipiell auch erst auf den Button Tippen, und dann anfangen mit der Bewegung, allerdings verwackelt man den Vorgang dann eher). Dabei sollte das Gerät gleichmäßig weiterbewegt werden, bis am unteren Bildschrimrand der Erfolg verkündet wird.
 Wenn man den Button drückt, wird dabei das linke Bild aufgenommen. Nach 3,5cm Bewegung wird das mittlere Bild und die Punktwolke gespeichert.
 Und nach weiteren 3,5cm wird das rechte Bild gespeichert.
 Die Bilder und Punktwolke werden unter sdcard/Pictures/ARProject abgelegt unter aus dem Referenzpunktnamen abgeleiteten Datien, wie
 point_0.png, point_0_l.png, point_0_r.png und point_0.obj.
 Der Name des nähesten Referenzpunktes wird im UI angezeigt.
 Der Zähler für die Punktnames wird bei jeden Neustart zurückgesetzt, sodass vorherige Punkte überschrieben werden.
 Mit dem Clear DPs (clear depth points) Button kann der Zähler auch direkt zurückgesetzt werden. Die gespeicherten Referenzpunktpositionen werden dabei auch gelöscht. Gespeicherte Dateien bleiben jedoch erhalten.
 
 ### Verdeckungsmodelle hinterlegen
 Angefertigte Verdeckungsmodelle werden unter assets/depthimages abgelegt, unter dem gleichen Namen, wie die dazugehörige Punktwolke.
 Also beispielsweise point_0.obj.
 
 ### Ankerpunkt erkennen
 Mit dem Resolve Button kann ein Ankerpunkt erkannt werden. Nach Antippen des Buttons wird nach dem zuletzt erstellten Ankerpunkt gesucht.
 Wird der Button gedrückt gehalten, kann in einem Dialog statdessen eine beliebige Ankerpunktnummer eingegeben werden.
 Das Erkennen funktioniert am besten, wenn die Umgebung bereitsgut abgescannt wurde und ARCore eine genaue Vorstellung von der Umgebung hat.
 Zur Zeit sind Ankerpunkte mit der CloudAnchor API nur einen Tag verfügar. Danach gibt ein Erkennungsversuch eine Fehlermeldung zurück.
 
 ### Debugansicht
 In der Debugansicht werden Verdeckungsmodelle, Flächen, Punktwolken, Referenzpunkte, Ankerpunkt und Origin angezeigt.
 Zudem werden einige Debugbuttons und -anziegen im UI sichtbar.
 Der Debugmodus kann mit dem Debug Button umgeschaltet werden.
