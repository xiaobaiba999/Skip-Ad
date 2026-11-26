package li.gkd.app.data

import li.gkd.app.model.DeviceInfo

actual fun currentDeviceInfo(): DeviceInfo =
    DeviceInfo(
        device = System.getProperty("os.name").orEmpty(),
        model = "Desktop",
        manufacturer = "jvm",
        brand = "jvm",
        sdkInt = 0,
        release = System.getProperty("os.version").orEmpty(),
    )
