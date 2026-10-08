# Guía de usuario: jugar con tu MOGA Pocket

Esta guía es para quien instala el APK de **MOGA Bridge** y quiere usar el mando como gamepad en juegos y emuladores. No necesitas root.

> **Compatibilidad:** el mapeo de botones está pensado solo para el **MOGA Pocket** (modo A). Otros modelos (por ejemplo el MOGA Pro) usan una disposición distinta y todavía no son compatibles.

## Qué necesitas

- Un móvil Android con Bluetooth y el APK de MOGA Bridge instalado.
- Un MOGA Pocket en **Modo A** (interruptor del mando en A).
- Para el modo gamepad: un PC con las **platform-tools de Android (adb)** instaladas y un cable USB. Es necesario porque Android solo deja crear un mando virtual al usuario de adb; la app no puede hacerlo sola. Hay que repetirlo cada vez que reinicias el móvil.

## Paso 1: conectar el mando

1. Abre **MOGA Bridge**, pestaña **Conexión**, y pulsa **Escanear**. Concede los permisos que pida (dispositivos cercanos y notificaciones).
2. Enciende el MOGA en modo de emparejamiento. Cuando aparezca, pulsa **Vincular y conectar** y confirma el aviso de Android (si pide PIN, prueba `0000`).
3. Con el mando conectado la app pasa sola a **Prueba** y aparece una notificación permanente con el botón **Desconectar**.
4. Mueve los sticks y pulsa botones: deben reaccionar en pantalla.

## Paso 2: activar el gamepad virtual (una vez por reinicio del móvil)

1. En el móvil: **Ajustes → Acerca del teléfono** y toca 7 veces el **número de compilación** para activar las opciones de desarrollador.
2. **Ajustes → Opciones de desarrollador →** activa **Depuración USB** (en algunos Xiaomi, también **Depuración USB (ajustes de seguridad)**).
3. En el PC instala las **platform-tools de Android** (incluyen `adb`): descárgalas gratis de [developer.android.com/tools/releases/platform-tools](https://developer.android.com/tools/releases/platform-tools) y descomprímelas.
4. Conecta el móvil por USB y acepta el aviso "¿Permitir depuración USB?".
5. En MOGA Bridge abre la pestaña **Mapeo**. Verás "Puente al sistema no iniciado" y un comando como:
   ```
   adb shell sh /sdcard/Android/data/dev.mogabridge.app/files/helper.sh
   ```
   Cópialo y ejecútalo en una terminal del PC (en la carpeta de platform-tools).
6. Debe responder `started`. Pulsa **Comprobar** en la app: pasará a "Puente al sistema listo". Ya puedes desconectar el cable.

## Paso 3: jugar

1. Con el mando conectado, abre tu juego o emulador. El mando se detecta solo como un gamepad estándar con sticks analógicos.
2. Si una app no reconoce algún botón, asígnalo en los ajustes de controles de esa app pulsando la acción y luego el botón del mando.
3. **Distribución de sticks** (pestaña Mapeo): elige *Dos analógicos*, *Analógico derecho + D-pad* (el stick izquierdo hace de cruceta) o *Analógico izquierdo + D-pad* (el derecho hace de cruceta). Útil para juegos que esperan una cruceta.
4. Para volver a MOGA Bridge y comprobar el mando sin que mueva la app, usa la pestaña **Prueba** y activa **Aislar el mando mientras pruebo**. Se desactiva solo al salir de esa pantalla o al cambiar de app.

## El mando se apaga solo

El MOGA tiene un temporizador interno: si pasa un rato sin recibir pulsaciones, **se apaga por sí mismo** para ahorrar batería. No es un fallo de la app. MOGA Bridge lo avisa y te devuelve a la pestaña **Conexión**; enciende el mando y vuelve a conectar.

## Si algo falla

| Síntoma | Qué hacer |
|---|---|
| "Puente al sistema no iniciado" | Repite el comando del paso 2; tras reiniciar el móvil hay que hacerlo de nuevo. |
| `Missing .../helper.token` | Abre MOGA Bridge una vez y vuelve a ejecutar el comando. |
| `adb: no devices` | Revisa el cable, la depuración USB y el aviso de autorización en el móvil. |
| El mando se conecta pero el juego no responde | Comprueba que **Aislar el mando** esté desactivado y que el modo sea **Gamepad virtual**. |
| Quiero parar el puente | `adb shell sh /sdcard/Android/data/dev.mogabridge.app/files/helper.sh stop` |

## Privacidad y seguridad

El ayudante solo escucha en el propio móvil (`127.0.0.1`) y exige una clave secreta que genera la app y guarda en su carpeta privada; otras apps no pueden usarlo. En Android 10 o anterior esa carpeta es menos privada, por lo que la protección es menor. El modo gamepad no envía datos a internet.
