definition(
    name: "Battery Monitor",
    namespace: "cse",
    author: "cse",
    description: "Monitors battery levels and sends alerts if below threshold",
    category: "My Apps",
    iconUrl: "https://example.com/icon.png",
    iconX2Url: "https://example.com/icon@2x.png"
)

preferences {
    section("Battery Alert Settings") {
        input "alertThreshold", "number", title: "Battery Alert Threshold (%)", required: true, defaultValue: 20
        input "notificationDevice", "capability.notification", title: "Notification Device", multiple: false, required: true
        input "checkInterval", "number", title: "How often to check battery levels and send alerts (in minutes)", required: true, defaultValue: 30
    }
    section("Devices to Monitor") {
        input "batteryDevices", "capability.battery", title: "Select Devices", multiple: true, required: true
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    scheduleCheckBatteryLevels()
    state.lastNotificationTime = 0
    checkBatteryLevels()
}

def scheduleCheckBatteryLevels() {
    def cronExpression = "0 0/${checkInterval} * * * ?"
    schedule(cronExpression, "checkBatteryLevels")
}

def checkBatteryLevels() {
    log.debug "Checking battery levels..."

    def lowBatteryDevices = []

    batteryDevices.each { device ->
        def batteryLevel = device.currentValue("battery")

        if (batteryLevel == null) {
            log.warn "${device.displayName} does not report battery level."
            return
        }

        log.debug "${device.displayName}: ${batteryLevel}%"

        if (batteryLevel < alertThreshold) {
            lowBatteryDevices << [name: device.displayName, level: batteryLevel]
        }
    }

    if (lowBatteryDevices) {
        sendAlert(lowBatteryDevices)
    } else {
        log.debug "All devices are above the threshold."
    }
}

def sendAlert(lowBatteryDevices) {
    def now = new Date().time
    def elapsedMinutes = (now - state.lastNotificationTime) / 60000

    if (elapsedMinutes < checkInterval) {
        log.debug "Skipping notification; last sent ${elapsedMinutes} minutes ago."
        return
    }

    def message = "Low battery alert:\n" + lowBatteryDevices.collect {
        "${it.name}: ${it.level}%"
    }.join("\n")

    log.debug message

    if (notificationDevice) {
        notificationDevice.deviceNotification(message)
    } else {
        log.warn "No notification device selected to send alerts."
    }

    state.lastNotificationTime = now
}