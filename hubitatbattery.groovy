definition(
    name: "Battery Monitor",
    namespace: "cse",
    author: "cse",
    description: "Monitors battery levels and sends alerts if below threshold",
    category: "My Apps",
    iconUrl: "https://example.com/icon.png",
    iconX2Url: "https://example.com/icon@2x.png",
    version: "1.1"
)

preferences {
    section("Battery Alert Settings") {
        input "alertThreshold", "number", title: "Battery Alert Threshold (%)", required: true, defaultValue: 20
        input "notificationDevices", "capability.notification", title: "Notification Devices", multiple: true, required: true
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
    state.lastNotificationTime = null // Reset notification timer on initialization
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
    def message = "Low battery alert:\n" + lowBatteryDevices.collect {
        "${it.name}: ${it.level}%"
    }.join("\n")

    log.debug message

    if (notificationDevices) {
        notificationDevices.each { device ->
            device.deviceNotification(message)
        }
    } else {
        log.warn "No notification devices selected to send alerts."
    }

    // Reset the notification timer after every alert
    state.lastNotificationTime = new Date().time
}
