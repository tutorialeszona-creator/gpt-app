# Mi Departamento 🏠

Aplicación Android nativa y sin conexión para administrar las cuentas del hogar. Primera versión funcional en español con Kotlin, Jetpack Compose, Material 3 y Room. Android 8.0 (API 26) o posterior.

## Abrir en Android Studio y generar el APK

1. Instalá Android Studio **Meerkat 2024.3.1 o posterior** y abrí esta carpeta como proyecto.
2. Usá el JDK integrado compatible con Gradle (17 o 21). En SDK Manager instalá **Android SDK Platform 35**, **Build Tools 35.0.0** y Platform Tools. Aceptá las licencias del SDK.
3. Esperá la sincronización de Gradle. Android Studio creará `local.properties` con la ubicación de tu SDK. Este archivo es local y no debe subirse a Git.
4. Seleccioná un teléfono o emulador Android 8 o posterior y presioná **Run**.
5. Para crear un APK instalable de prueba: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. También podés usar **Build → Generate App Bundles or APKs → Generate APKs**, según la versión de Studio.

Desde terminal, con `ANDROID_HOME` configurado (Windows: `gradlew.bat`):

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Es una compilación de desarrollo, firmada automáticamente para pruebas. Para distribuir una versión definitiva, usá **Generate Signed App Bundle / APK**, creá y guardá tu propia clave de firma fuera del repositorio. No hay claves de producción incluidas.

El wrapper fija Gradle 8.11.1. El proyecto fija Android Gradle Plugin 8.9.1, Kotlin/Compose 2.1.20 y Room 2.7.0. La primera compilación necesita internet para descargar herramientas y dependencias; la app instalada no lo necesita y no solicita permiso de internet.

## Primer uso

- Se crean siete conceptos mensuales: alquiler $460.000, expensas $80.000, internet $26.000, electricidad $50.000, gas $7.500, agua $32.500 y ABL $12.000. Total inicial previsto: **ARS 668.000**.
- Electricidad, gas y agua están identificados como estimados. Ninguna boleta aparece pagada y **no se asignan vencimientos**.
- Se crea un ingreso mensual base de **USD 1.000**. Ingresá la cotización en Configuración. Hasta entonces se advierte que los totales en pesos son parciales; no se inventa un tipo de cambio.
- Desde Inicio se cambia el mes/año y se registran ingresos o gastos. En Boletas se editan importes, vencimientos, notas y pagos. Para pagar se exige confirmar el importe real y una fecha de pago válida, no futura.
- Se pueden registrar ingresos adicionales en ARS/USD y gastos personales de las ocho categorías solicitadas. Los movimientos se pueden corregir y eliminar.
- Los PDF, JPG, PNG y WebP (máximo 20 MB por archivo) se copian al almacenamiento privado de la app. Se pueden abrir con una aplicación externa compatible, reemplazar y exportar con la copia de seguridad.

## Cálculos y recurrencia

- **Previstos:** importe real de cada boleta si se cargó; de lo contrario, previsto. Se agregan los gastos personales del mes.
- **Reales:** boletas pagadas, por su importe real, más gastos personales. Marcar una boleta pagada no crea otro gasto.
- **Disponible previsto:** ingresos − gastos previstos. **Saldo actual:** ingresos − gastos reales; no se presenta como ahorro definitivo mientras existan boletas pendientes.
- Los montos se guardan como centavos enteros y la conversión USD→ARS usa `BigDecimal` con redondeo HALF_UP al centavo.
- La cotización es global y manual: modificarla revaloriza también los ingresos en USD de meses anteriores. Esta versión no almacena una cotización histórica por ingreso.
- Un mes se genera en una transacción al visitarlo por primera vez. Una marca de mes y un índice único concepto/mes evitan duplicados. Volver a abrirlo no repone movimientos que eliminaste intencionalmente.
- Las frecuencias son de 1, 2, 3, 6 o 12 meses desde el mes de inicio del concepto. Los cambios de plantillas o del ingreso base afectan **meses que todavía no se generaron**. Las boletas de meses existentes se editan individualmente.
- Desactivar un concepto conserva sus boletas. Mover una boleta a otro mes la desvincula de la plantilla; no genera automáticamente un reemplazo en el mes original.
- Una boleta vence sólo si hay fecha cargada anterior a hoy y sigue sin pagar. Las fechas usan el calendario local del teléfono.

## Estructura

```text
app/src/main/java/ar/com/midepartamento/
  DepartmentApplication.kt Base de datos por instancia de aplicación
  MainActivity.kt          Entrada de la app
  data/Database.kt         Entidades, DAO y base Room
  data/Repository.kt       Generación mensual, pagos, adjuntos y exportación
  domain/Finance.kt        Dinero, conversión, recurrencia y totales
  ui/FinanceViewModel.kt   Estado reactivo y operaciones
  ui/Screens.kt            Inicio, boletas, movimientos, estadísticas y configuración
  ui/EditDialog.kt         Formularios y validación
  ui/Theme.kt              Tema Material 3 oscuro
app/src/test/              Reglas financieras y pruebas de persistencia con Room/Robolectric
app/schemas/              Esquema versionado de Room
```

## Datos y copia de seguridad

Todos los datos se guardan en Room (`mi-departamento.db`). Cerrar la app o reiniciar el teléfono conserva la información. Desinstalar o borrar los datos de la app los elimina.

Configuración → **Exportar copia de seguridad** permite elegir dónde guardar un JSON versión 1 con todos los meses, plantillas, movimientos, fechas, notas, historial y comprobantes en Base64. Es un archivo sin cifrar: guardalo de forma privada. Los importes se exportan en centavos. La restauración/importación desde la interfaz queda pendiente para la siguiente versión; no desinstales confiando en una restauración automática. Las copias automáticas de Android están desactivadas para no enviar tus finanzas a la nube sin una decisión explícita.

## Alcance de esta versión

Incluye las cinco pantallas, gráfico de distribución, comparación de seis meses registrados y evolución por período de electricidad, gas y agua. No incluye notificaciones, OCR, sincronización, importación, pagos parciales ni conversión histórica. El historial registra pagos, correcciones y anulaciones; no es un libro contable inmutable y se elimina junto con la boleta si la borrás.

Consultá [VALIDATION.md](docs/VALIDATION.md) para las comprobaciones realizadas en este entorno.
