// Copyright 2016-2019 Hubitat Inc.  All Rights Reserved

metadata {
    definition (name: "Netatmo Home Coach", namespace: "Caistle", author: "Simon Darby", importURL: "https://raw.githubusercontent.com/Caistle/drivers/main/NetatmoHomeCoach.groovy?token=AU7T2OGTB3KF3EYG77UCP5TBXMHEA") {
        capability "Presence Sensor"
        capability "Carbon Dioxide Measurement"
        capability "Relative Humidity Measurement"
        capability "Temperature Measurement"
        capability "SoundPressureLevel"
        capability "PressureMeasurement"
        command "arrived"
        command "departed"
        command "setCarbonDioxide", ["Number"]
        command "setRelativeHumidity", ["Number"]
        command "setTemperature", ["Number"]
        command "setSoundPressureLevel", ["Number"]
        command "setPressure", ["Number"]
        attribute "variable", "String"
    }
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def installed() {
    log.warn "installed..."
    arrived()
    accelerationInactive()
    COClear()
    close()
    setIlluminance(50)
    setCarbonDioxide(350)
    setRelativeHumidity(35)
    motionInactive()
    smokeClear()
    setTemperature(70)
    dry()
    runIn(1800,logsOff)
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)
}

def parse(String description) {
}

def arrived() {
    def descriptionText = "${device.displayName} has arrived"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "presence", value: "present",descriptionText: descriptionText)
}

def departed() {
    def descriptionText = "${device.displayName} has departed"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "presence", value: "not present",descriptionText: descriptionText)
}

def setCarbonDioxide(CO2) {
    def descriptionText = "${device.displayName}  Carbon Dioxide is ${CO2} ppm"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "carbonDioxide", value: CO2, descriptionText: descriptionText, unit: "ppm")
}

def setRelativeHumidity(humid) {
    def descriptionText = "${device.displayName} is ${humid}% humidity"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "humidity", value: humid, descriptionText: descriptionText, unit: "RH%")
}

def setTemperature(temp) {
    def unit = "°${location.temperatureScale}"
    def descriptionText = "${device.displayName} is ${temp}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "temperature", value: temp, descriptionText: descriptionText, unit: unit)
}

def setSoundPressureLevel(noise) {
    def unit = "dB"
    def descriptionText = "${device.displayName} is ${noise}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "soundPressureLevel", value: noise, descriptionText: descriptionText, unit: unit)
}

def setPressure(air) {
    def unit = "mbar"
    def descriptionText = "${device.displayName} is ${air}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "pressure", value: air, descriptionText: descriptionText, unit: unit)
}
