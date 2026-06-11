# SecureMesh BLE Debug App

Лёгкое Android-приложение для теста ESP32 BLE Gateway проекта SecureMesh.

## Что умеет

- Показывает все BLE-устройства вокруг, включая Unknown / name = null.
- Показывает name, address, RSSI, nameOK, serviceOK.
- Можно нажать на устройство и подключиться вручную.
- Поддерживает auto-connect к:
  - SecureMesh-GW-A7
  - SecureMesh_BLE
  - service UUID 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
- После подключения показывает services и characteristics.
- Ищет RX WRITE characteristic и TX NOTIFY characteristic.
- Включает notifications через CCCD.
- Отправляет AUTH:7391.
- Отправляет команды:
  - TO:ALL;MSG:HELP
  - TO:ALL;MSG:OK
  - TO:ALL;MSG:MEDIC
  - TO:ALL;MSG:TEST
  - custom TO:<target>;MSG:<message>

## ESP32 BLE данные

Device name:

```text
SecureMesh-GW-A7
```

Service UUID:

```text
6E400001-B5A3-F393-E0A9-E50E24DCCA9E
```

RX Android -> ESP32 WRITE:

```text
6E400002-B5A3-F393-E0A9-E50E24DCCA9E
```

TX ESP32 -> Android NOTIFY:

```text
6E400003-B5A3-F393-E0A9-E50E24DCCA9E
```

Password:

```text
7391
```

## Как залить в GitHub

1. Создай новый repository на GitHub.
2. Распакуй ZIP.
3. В корень репозитория загрузи содержимое папки `SecureMeshAndroidDebug`, чтобы сразу было видно:

```text
.github
app
build.gradle
settings.gradle
README.md
```

4. Нажми `Commit changes`.

## Как собрать APK

1. Открой вкладку `Actions`.
2. Выбери `Build Android APK`.
3. Нажми `Run workflow`.
4. Когда сборка завершится, справа будет `Artifacts`.
5. Скачай `SecureMesh-debug-apk`.
6. Распакуй ZIP-артефакт.
7. Установи `app-debug.apk` на Samsung.

## Как проверить

1. Загрузи BLE Gateway код в ESP32.
2. В Serial Monitor должно быть:

```text
Device name: SecureMesh-GW-A7
Waiting for Android app...
```

3. На телефоне включи Bluetooth.
4. Дай приложению Nearby devices / Bluetooth permissions.
5. Нажми `Start Scan`.
6. В списке должны появляться BLE-устройства.
7. Нажми на `SecureMesh-GW-A7` или включи Auto-connect.
8. После подключения нажми `Auth`.
9. В log должно появиться:

```text
TX: AUTH:7391
RX: AUTH OK
```

10. Нажми HELP. Ожидаемый log:

```text
TX: TO:ALL;MSG:HELP
RX: RX OK ...
RX: ACK OK
```

## Если не видит ESP32

- Включи Bluetooth.
- Включи Location / Геолокацию на телефоне.
- Дай приложению Nearby devices / Устройства поблизости.
- Закрой nRF Connect полностью.
- Перезагрузи ESP32.
- Проверь, видно ли `SecureMesh-GW-A7` в nRF Connect.
- В приложении включи `Show all` и нажми `Start Scan`.
- Если приложение показывает вообще ноль устройств — проблема в permissions или Bluetooth.
- Если показывает другие устройства, но не ESP32 — проблема в advertising ESP32 или ESP32 уже подключена к nRF Connect.
