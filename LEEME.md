# AvisaPagos 🔊

App Android que **anuncia en voz alta los pagos que recibes** por Yape, Plin (Interbank, BBVA, Scotiabank) y BCP, parecido al parlante de Izipay.

- Suena un "ding" y dice: *"Yape. Recibiste 25 soles con 50 céntimos, de Juan Pérez"*.
- Muestra el **total recibido hoy**, cuántos pagos hubo y el desglose por app.
- Guarda el historial en el celular (no usa internet ni pide tus claves).
- Ajustes: volumen al máximo al anunciar, repetir 2 veces, decir o no el nombre.

**Cómo funciona:** la app lee las notificaciones que ya te manda Yape/Plin/tu banco. No se conecta a tu cuenta, así que es segura. **Importante:** tiene que estar instalada **en el mismo celular donde tienes Yape** (o la app del banco) con las notificaciones activadas. Si quieres que suene en la caja del restaurante, ese celular tiene que quedarse en la caja.

---

## Paso 1 — Obtener el APK (el instalador)

### Opción A: con GitHub (gratis, no instalas nada en tu PC)
1. Crea una cuenta en **github.com**.
2. Arriba a la derecha: **+ › New repository** → nombre `AvisaPagos` → **Create repository**.
3. Haz clic en **"uploading an existing file"** y **arrastra todo el contenido** de la carpeta `AvisaPagos` (descomprimida), incluida la carpeta `.github`. Luego **Commit changes**.
4. Ve a la pestaña **Actions**. Verás "Compilar APK" trabajando. Espera unos 5 minutos a que salga el ✅ verde.
5. Desde el celular, entra a tu repositorio → **Releases** (a la derecha) → descarga **AvisaPagos.apk**.

> Si en *Actions* no aparece nada, es que la carpeta `.github` no se subió. Solución: en *Actions* elige **"set up a workflow yourself"**, pega el contenido del archivo `.github/workflows/build.yml` y guarda.

### Opción B: con Android Studio
Abre la carpeta en Android Studio → **Build › Build APK(s)**. El APK queda en `app/build/outputs/apk/debug/`.

---

## Paso 2 — Instalar y configurar en el celular

1. Abre `AvisaPagos.apk` y acepta **"Permitir instalar apps de esta fuente"**.
2. Abre AvisaPagos y toca **"Activar acceso a notificaciones"** → activa **AvisaPagos**.
   - En Android 13 o más nuevo, si sale *"Configuración restringida"*: ve a **Ajustes › Apps › AvisaPagos › ⋮ (arriba a la derecha) › Permitir configuración restringida**, y vuelve a intentarlo.
3. Toca **"Evitar que Android la apague (batería)"** → **Permitir**.
   - En Xiaomi/Redmi/POCO: además activa **Inicio automático** y pon Batería en **"Sin restricciones"**.
   - En Samsung: Ajustes › Batería › quita AvisaPagos de las apps "en suspensión".
4. Revisa que **Yape tenga las notificaciones activadas** (en Yape y en los ajustes de Android).
5. Toca **"Probar voz"**. Si habla en inglés o no habla: instala/actualiza **"Servicios de voz de Google"** y descarga la voz en español.
6. Prueba real: pídele a alguien que te yapee S/ 1.

---

## Si un pago no se anunció
Baja a **"Notificaciones no reconocidas"** en la app. Mantén presionado el texto para copiarlo y mándamelo: ajusto las reglas para ese formato.

Si recibes pagos por otra app (una Caja, BanBif, etc.), activa **"Escuchar también otras apps"**.

## Actualizar la app
Si al instalar una versión nueva dice que "no se puede actualizar", desinstala la anterior y vuelve a instalar (el historial se borra).

---

### Para programadores
- Kotlin, sin librerías externas. minSdk 24 (Android 7), targetSdk 35.
- `PaymentListenerService` (NotificationListenerService) → `PaymentParser` (reconoce montos y frases de pago recibido, ignora pagos enviados, cobros y publicidad) → `Store` (historial en SharedPreferences) → `Announcer` (ding + TextToSpeech).
- Paquetes escuchados por defecto: ver `PaymentParser.KNOWN_APPS`.
