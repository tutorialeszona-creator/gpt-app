# Validación de la primera versión

## Entorno

Linux x86_64, Eclipse Temurin JDK 21.0.13, Gradle 8.11.1, Android SDK Platform 35 y Build Tools 35.0.0. Las herramientas se descargaron de sus publicaciones oficiales y se verificaron sus checksums; se mantuvo la validación TLS.

En el entorno cloud:

```sh
cd /workspace/gpt-app
python3 scripts/cloud_setup.py
# Para comandos posteriores:
source scripts/cloud-env.sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

El script prepara o reutiliza las herramientas en `/workspace/toolchains`, conserva `local.properties` si ya existe y ejecuta pruebas, Lint y construcción del APK. Cada tarea cloud ya está aislada: usá este checkout, sin crear otro worktree salvo pedido explícito.

## Cobertura automatizada

- `FinanceTest`: importes y decimales, conversión/redondeo, frecuencia entre años, vencimientos, cotización faltante y cálculo sin duplicar pagos.
- `RepositoryTest` con Room real bajo Robolectric: generación concurrente idempotente, siete conceptos e importes exactos, estimados y fechas vacías, registro y anulación de pagos, no resucitar boletas eliminadas, frecuencia bimestral, preservación de meses anteriores, lectura desde otra instancia de la base en disco, edición/borrado y exportación JSON con un comprobante Base64.
- `AppSmokeTest` con Compose bajo Robolectric: ingreso por formulario, rechazo de importe inválido y creación del ingreso correcto.
- `MainActivityTest`: arranque real de la actividad, carga de la base y navegación a Boletas y Configuración, con desplazamiento en pantallas pequeñas.

Resultado final en esta instancia: **10 pruebas ejecutadas, 10 correctas, 0 fallidas, 0 errores y 0 omitidas**. `lintDebug` y `assembleDebug` completados correctamente. Lint finalizó sin errores y con una advertencia sobre la configuración de copias en versiones anteriores de Android. Las copias automáticas están deshabilitadas explícitamente con `allowBackup=false`, `fullBackupContent=false` y reglas de exclusión para Android 12 o posterior. Se ejecutó el instalador reutilizable completo `python3 scripts/cloud_setup.py`, incluido el Gradle Wrapper con verificación SHA-256.

APK de desarrollo: `app/build/outputs/apk/debug/app-debug.apk` (aproximadamente 16 MB).
SHA-256: `3bce52cc06cbd2e9bd1f9bed12da87b20048d7fe058c6b75bc2c3aa98366f5b1`.

La configuración reutilizable de instalación, arranque y ubicación del checkout se guardó en el borrador del entorno. Guardar el borrador no publica el snapshot; la publicación y la validación en una nueva tarea quedan fuera de esta comprobación.

## Prueba manual en Android Studio o teléfono (pendiente)

No se ha probado aún en un teléfono ni en un emulador Android. Este entorno no dispone de `/dev/kvm`. Robolectric ejecuta pruebas de Android y Compose en la JVM; no sustituye una comprobación visual y funcional en un dispositivo.

Con una instalación nueva:

1. Inicio: previsto ARS 668.000, real ARS 0, siete boletas pendientes, cero pagadas. USD 1.000 y advertencia de cotización faltante. No debe haber fechas de vencimiento.
2. Configuración: ingresar cotización **1000**. Deben verse ingresos ARS 1.000.000 y disponible previsto ARS 332.000.
3. Boletas: electricidad debe figurar estimada. Ingresar importe real **48000**, marcar pagada y elegir la fecha efectiva. Esperado: previstos ARS 666.000, reales ARS 48.000, disponible ARS 334.000. No agregar ese pago como gasto personal.
4. Movimientos: agregar supermercado **1250**. Esperado: previstos ARS 667.250, reales ARS 49.250 y disponible ARS 332.750.
5. Cerrar y abrir la app. Los registros, la cotización y los pagos deben conservarse.
6. Volver al mismo mes varias veces: no debe haber boletas duplicadas. Eliminar una boleta y regresar: no debe reaparecer.
7. Adjuntar un PDF o imagen, cerrar la app y volver a abrir el comprobante. Exportar la copia JSON mediante el selector de archivos de Android.
8. Cambiar frecuencia de un concepto y abrir un mes todavía no generado. Confirmar que se aplica la frecuencia nueva y que las boletas anteriores se conservan.
9. Probar pantalla pequeña, tamaño de fuente grande, teclado, rotación, navegación inferior y selector de fechas.

## Pendientes conocidos

Importación/restauración de copias desde la interfaz, notificaciones, OCR, pagos parciales y cotización histórica por movimiento. Exportar una copia funciona como respaldo portable, pero esta versión todavía no incluye su restauración automática.
