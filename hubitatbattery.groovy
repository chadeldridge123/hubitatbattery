definition(
    name: "Battery Monitor",
    namespace: "cse",
    author: "cse",
    description: "Monitors battery levels and sends alerts if below threshold",
    category: "My Apps",
    iconUrl: "https://example.com/icon.png",
    iconX2Url: "https://example.com/icon@2x.png",
    version: "1.4"
)

preferences {
    section("Battery Alert Settings") {
        input "alertThreshold", "number", title: "Default Battery Alert Threshold (%)", required: true, defaultValue: 20
        input "notificationDevices", "capability.notification", title: "Notification Devices", multiple: true, required: true
        input "checkInterval", "number", title: "How often to check battery levels and send alerts (in minutes)", required: true, defaultValue: 30
    }
    section("Devices with Default Threshold") {
        input "batteryDevices", "capability.battery", title: "Select Devices", multiple: true, required: true
    }
    section("Devices with Custom Threshold") {
        input "customThresholdDevices", "capability.battery", title: "Select Devices with Custom Thresholds", multiple: true, required: false
        input "customThresholdValues", "text", title: "Custom Threshold Values (comma-separated, matching order of selected devices, e.g., '15, 25, 30' for Device A=15%, Device B=25%, Device C=30%)", required: false
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

def parseCustomThresholds() {
    def thresholds = [:]
    if (customThresholdDevices && customThresholdValues) {
        def devices = customThresholdDevices.collect { it.displayName }
        def values = customThresholdValues.split(",").collect { it.trim().toInteger() }

        if (devices.size() == values.size()) {
            devices.eachWithIndex { deviceName, idx ->
                thresholds[deviceName] = values[idx]
            }
        } else {
            log.warn "Custom thresholds configuration mismatch: device and value counts do not match."
        }
    }
    return thresholds
}

def checkBatteryLevels() {
    log.debug "Checking battery levels..."

    def thresholds = parseCustomThresholds()
    def lowBatteryDevices = []

    batteryDevices.each { device ->
        def batteryLevel = device.currentValue("battery")

        if (batteryLevel == null) {
            log.warn "${device.displayName} does not report battery level."
            return
        }

        def deviceThreshold = thresholds[device.displayName] ?: alertThreshold
        log.debug "${device.displayName}: ${batteryLevel}%, Threshold: ${deviceThreshold}%"

        if (batteryLevel < deviceThreshold) {
            lowBatteryDevices << [name: device.displayName, level: batteryLevel]
        }
    }

    if (lowBatteryDevices) {
        sendAlert(lowBatteryDevices)
    } else {
        log.debug "All devices are above their respective thresholds."
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
