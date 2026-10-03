# Mi Band Maps

Android-16-Begleit-App für Xiaomi Mi Band 9. Sie liest laufende Google-Maps-Navigationshinweise über Androids NotificationListenerService und veröffentlicht sie als normale Benachrichtigung, die Mi Fitness ans Band spiegeln kann.

Akkuschonend: kein eigenes GPS, keine Standortberechtigung, kein Internetzugriff, kein Polling und keine eigene dauerhafte Bluetooth-Verbindung.

Version 1.3.0 sendet eine Startmeldung, pro Abbiege-Manöver einmal bei höchstens 100 m und einmal bei höchstens 20 m oder einem ausdrücklichen „Jetzt“-Hinweis sowie einmal die Ankunft. Übersprungene Schwellen werden nicht nachträglich gesendet. Distanz-, Restzeit- und ETA-Updates bleiben stumm. Die Entfernungsgrenzen sind feste Werte; es gibt keine zusätzlichen Einstellungen.

In Mi Fitness nur **Mi Band Maps** für die Weiterleitung aktivieren und **Google Maps** deaktivieren. Sonst können zusätzlich die ungefilterten Originalmeldungen auf dem Band erscheinen. Die gefilterten Meldungen tragen den Titel **Mi Band Maps**.

Die Erkennung basiert auf dem von Google Maps bereitgestellten Benachrichtigungstext (Deutsch/Englisch). Ohne erkennbare Entfernung oder „Jetzt“-Hinweis lässt sich die Abbiegeschwelle nicht bestimmen. Aufeinanderfolgende gleichlautende Abbiegungen werden anhand eines deutlichen Distanzanstiegs oder eines dazwischenliegenden Geradeaus-Schritts erkannt.

GitHub Actions baut mit SDK 36 und Java 17, prüft die APK-Signatur und führt die Meldungsfolgen aus `tests/NavigationGateTest.java` aus. Der Cache-Schlüssel für den bestehenden Debug-Signierschlüssel bleibt unverändert.
