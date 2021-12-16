/**
 * Heiman Zigbee Smoke Detector
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *   Original DTH on SmartThings by cuboy29
 *       Converted to Hubitat and extensivly modified by Scruffy-SJB
 *
 *   Code snippets copied from veeceeoh, Daniel Terryn and Marcus Liljergren - with thanks.
 */

// Ver: 1.8 - Added fingerprint for model SmokeSensor-EF-3.0  Enhanced status message processing.  New Current State - Date/Time last status recorded.
// Ver: 1.7 - Added fingerprint for model HS1CA-M HEIMAN Smart Carbon Monoxide Sensor
// Ver: 1.6 - Added fingerprint for model HS1SA-M
// Ver: 1.5 - Added ability to detect a device test and to trigger a last tested event
// Ver: 1.4 - Parsed another unhandled catch-all
// Ver: 1.3 - Updated to support Zigbee 3.0 version HS1SA-E

import hubitat.zigbee.clusters.iaszone.ZoneStatus
 
metadata {
	definition (name: "Heiman Zigbee Smoke Detector", namespace: "scruffy-sjb", author: "scruffy-sjb and cuboy29", importURL: "https://raw.githubusercontent.com/Caistle/drivers/main/HeimanZigbeeSmokeDetector.groovy?token=AU7T2OC4L7HCBAMMO4YWO4TBXIEZY") {
		
        capability "Configuration"
        capability "Smoke Detector"
        capability "SmokeDetector"
        capability "Sensor"
        capability "Refresh"
        capability "Battery"
        
		command "resetToClear"
        command "resetBatteryReplacedDate"
        
        attribute "smoke", "string"
        attribute "batteryLastReplaced", "string"
        attribute "sensorLastTested", "string"
        attribute "lastStatus", "string"
          
        fingerprint profileID: "0104", deviceID: "0402", inClusters: "0000,0001,0003,0500,0502", outClusters: "0019", manufacturer: "HEIMAN", model: "SmokeSensor-EM", deviceJoinName: "HEIMAN Smoke Detector" //HEIMAN Smoke Sensor (HS1SA-E)
        fingerprint profileID: "0104", deviceID: "0402", inClusters: "0000,0003,0500,0001,0009,0502", outClusters: "0019", manufacturer: "HEIMAN", model: "SMOK_V16", deviceJoinName: "HEIMAN Smoke Detector M" //HEIMAN Smoke Sensor (HS1SA-M)
        fingerprint profileID: "0104", deviceID: "0402", inClusters: "0000,0003,0001,0500,0502,0B05", outClusters: "0019", manufacturer: "HEIMAN", model: "SmokeSensor-N-3.0", deviceJoinName: "HEIMAN Smoke Detector 3.0" //HEIMAN Smoke Sensor (HS1SA-E)
        fingerprint profileID: "0104", deviceID: "0402", inClusters: "0000,0001,0003,0500", manufacturer: "HEIMAN", model: "COSensor-EM", deviceJoinName: "HEIMAN CO Sensor" //HEIMAN Smart Carbon Monoxide Sensor (HS1CA-E)
        fingerprint profileID: "0104", deviceID: "0402", inClusters: "0000,0001,0003,0500,0502,0B05", outClusters: "0019", manufacturer: "HEIMAN", model: "SmokeSensor-EF-3.0", deviceJoinName: "HEIMAN Smoke Detector" //HEIMAN Smoke Sensor 
    }
}

def SensorTestOptions = [:]
	SensorTestOptions << ["1" : "Yes"] // 0x01
	SensorTestOptions << ["0" : "No"]  // 0x00

preferences {
	input "SensorTest", "enum", title: "Enable Sensor Testing", options: SensorTestOptions, description: "Default: Yes", required: false, displayDuringSetup: true
}        

def parse(String description) {
    def descMap = [:]
    
	if (description?.startsWith('zone status')) {
			descMap = parseIasMessage(description)
    }else if (description?.startsWith('enroll request')) {
		    List cmds = zigbee.enrollResponse()
		    descMap = cmds?.collect { new hubitat.device.HubAction(it) }
	}else if (description?.startsWith('catchall')) {
            descMap = parseCatchAllMessage(description)
    }else if (description?.startsWith('read attr'))  {  
            descMap = zigbee.parseDescriptionAsMap(description)
            if ((descMap?.cluster == "0500" && descMap.attrInt == 0x0001) && (descMap.value == '0028')){  //Zone Type
                log.debug "Zone Type is Fire Sensor"
			}else if ((descMap?.cluster == "0500" && descMap.attrInt == 0x0000) && (descMap.value == '01')){  //Zone State
                log.debug "Zone State is enrolled"
			}else if ((descMap?.cluster == "0500" && descMap.attrInt == 0x0002) && ((descMap.value == '20') || (descMap.value == '0020'))){  //Zone Status Clear
                SmokeOrClear("clear")    
			}else if ((descMap?.cluster == "0500" && descMap.attrInt == 0x0002) && ((descMap.value == '30') || (descMap.value == '0030'))){  //Zone Status Clear
                SmokeOrClear("clear") 
			}else if ((descMap?.cluster == "0500" && descMap.attrInt == 0x0002) && ((descMap.value == '0031') || (descMap.value == '0021'))){  //Zone Status Smoke
                SmokeOrClear("detected")              
			}else if ((descMap?.cluster == "0502" && descMap.attrInt == 0x0000)){  //Alarm Max Duration
                def int alarmMinutes = Integer.parseInt(descMap.value,16) 
                log.debug "Max Alarm Duration is ${alarmMinutes} seconds"              
			}else if ((descMap?.cluster == "0000" && descMap.attrInt == 0x0007) && ((descMap.value == '03') )){  //PowerSource
                log.debug "${device.displayName} is Battery Powered"    
			}else if ((descMap?.cluster == "0001" && descMap.attrInt == 0x0020)) {  //Battery Voltage
                def batteryVoltage = ConvertHexValue(descMap.value)
                handleBatteryEvent(batteryVoltage)
			}else if ((descMap?.cluster == "0001" && descMap.attrInt == 0x0021)) {  //Battery Cells
                def batteryCells = ConvertHexValue(descMap.value)
                handleCellsEvent(batteryCells)                
            }else if (descMap?.cluster == "0000" && descMap.attrInt == 0x0004){  //Manufacture
                sendEvent(name: "manufacture", value: descMap.value)
                log.debug "Manufacturer is ${descMap.value}"
            }else if (descMap?.cluster == "0000" && descMap.attrInt == 0x0005){  //Model 
                sendEvent(name: "model", value: descMap.value)
                log.debug "Model is ${descMap.value}"
            }else {log.debug "Unknown --> Cluster-> ${descMap?.cluster}  AttrInt-> ${descMap.attrInt}  Value-> ${descMap.value}"
            }
       // log.debug "Cluster-> ${descMap?.cluster}  AttrInt-> ${descMap.attrInt}  Value-> ${descMap.value}"
    }else { 
        log.debug "Unparsed -> $description" 
        descMap = zigbee.parseDescriptionAsMap(description)
    }
    // log.debug "$descMap"
	return descMap   
}    

private parseCatchAllMessage(String description) {
    
    Map resultMap = [:]
    def descMap = zigbee.parse(description)  
    if (shouldProcessMessage(descMap)) {
        log.debug descMap.inspect()               
    }
    return resultMap
}

private boolean shouldProcessMessage(cluster) {
    // 0x0B is default response indicating message got through
    // 0x07 is bind message
    // 0x04 - No Idea !!!!!
    boolean ignoredMessage = cluster.profileId != 0x0104 || 
        cluster.command == 0x0B ||
        cluster.command == 0x07 ||
        cluster.command == 0x04 ||        
        (cluster.data.size() > 0 && cluster.data.first() == 0x3e)
    return !ignoredMessage
}

def refresh() {
	log.debug "Refreshing..."
	def refreshCmds = []
    
    refreshCmds +=
	zigbee.readAttribute(0x0500, 0x0001) +	   // IAS ZoneType
    zigbee.readAttribute(0x0500, 0x0000) +	   // IAS ZoneState
    zigbee.readAttribute(0x0500, 0x0002) +	   // IAS ZoneStatus
    zigbee.readAttribute(0x0502, 0x0000) +	   // Alarm Max Duration
    zigbee.readAttribute(0x0000, 0x0007) +	   // Power Source
    zigbee.readAttribute(0x0001, 0x0020) +     // Battery Voltage      
    zigbee.readAttribute(0x0001, 0x0033) +     // Battery Cells         
    zigbee.readAttribute(0x0000, 0x0004) +	   // Manufacturer Name
    zigbee.readAttribute(0x0000, 0x0005) +	   // Model Indentification
    zigbee.enrollResponse()
    
	return refreshCmds
}

def configure() {
    log.debug "Configuring..."
    
	if (!device.currentState('batteryLastReplaced')?.value)
		resetBatteryReplacedDate(true)
    
    def cmds = [
            //bindings
            "zdo bind 0x${device.deviceNetworkId} 1 1 0x0500 {${device.zigbeeId}} {}", "delay 200",
        ] +  zigbee.enrollResponse(1200) + zigbee.configureReporting(0x0500, 0x0002, 0x19, 0, 3600, 0x00) + "delay 200" + 
        zigbee.configureReporting(0x0001, 0x0020, 0x20, 600, 7200, 0x01) + refresh()
    
    return cmds 
}

def resetBatteryReplacedDate(paired) {
	def newlyPaired = paired ? " for newly paired sensor" : ""
	sendEvent(name: "batteryLastReplaced", value: new Date())
	log.debug "${device.displayName} Setting Battery Last Replaced to Current date${newlyPaired}"
}

def resetSensorTestedDate() {
    def newlyTested=""
    sendEvent(name: "sensorLastTested", value: new Date())
    log.debug "${device.displayName} Setting Sensor Last Tested to Current date${newlyTested}"
}

def resetToClear() {
	sendEvent(name:"smoke", value:"clear")
    sendEvent(name: "lastStatus", value: new Date(), displayed: True)
    log.debug "Resetting to Clear..."
	didWeGetClear = 0
}

/**
 * Code borrowed (mixed and matched) from both Daniel Terryn and veeceeoh
 *
 * Create battery event from reported battery voltage.
 *
 */

private handleBatteryEvent(rawVolts) {
    rawVolts = rawVolts / 10.0
	def minVolts = voltsmin ? voltsmin : 2.5
	def maxVolts = voltsmax ? voltsmax : 3.0
	def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
	def roundedPct = Math.min(100, Math.round(pct * 100))
	log.debug "Battery level is ${roundedPct}% (${rawVolts} Volts)"
	
    sendEvent(name:"battery", value: roundedPct, unit: "%", isStateChange: true)
	return 
}

private handleCellsEvent(noCells) {
	log.debug "Battery reports that it has (${noCells} Cells)"
	return 
}

def ConvertHexValue(value) {
	if (value != null)
	{
		return Math.round(Integer.parseInt(value, 16))
	}
}

def SmokeOrClear(value) {
    if (value == "clear") {
        sendEvent(name:"smoke", value:"clear", isStateChange: true)
        log.info "${device.displayName} reports status is all clear"
    } else {
        sendEvent(name:"smoke", value:"detected", isStateChange: true)
        log.info "${device.displayName} reports smoke is detected"
    }
    
    sendEvent(name: "lastStatus", value: new Date(), displayed: True)
}

private Map parseIasMessage(String description) {
    // log.debug "Zone Status Received--> ${description}"
    ZoneStatus zs = zigbee.parseZoneStatus(description)    
    translateZoneStatus(zs)    
}

private Map translateZoneStatus(ZoneStatus zs) {
	// Some sensor models that use this DTH use alarm1 and some use alarm2 to signify motion       
	return getSmokeResult(zs.isAlarm1Set() || zs.isAlarm2Set())
}

private Map getSmokeResult(value) {
	if (value) {
        if (SensorTest == "") {
            SensorTest = "1"    // Default is Yes
        }
        
        if (SensorTest == "1") {
    		def descriptionText = "${device.displayName} status is pending"
            sendEvent(name: "lastStatus", value: new Date(), displayed: True)
	    	log.debug descriptionText
		    runIn(3,EventOrTest)
		    return [
			    name			: 'smoke',
			    value			: 'pending',
                isStateChange: false,
			    descriptionText : descriptionText
            ]
        } else {
    		def descriptionText = "${device.displayName} testing disabled - smoke detected !!!"
	    	log.debug descriptionText
            sendEvent(name: "lastStatus", value: new Date(), displayed: True)
		    return [
			    name			: 'smoke',
			    value			: 'detected',
                isStateChange: true,
			    descriptionText : descriptionText
            ]
        }                 
   } else {
		def descriptionText = "${device.displayName} all clear"
		log.debug descriptionText
        sendEvent(name: "lastStatus", value: new Date(), displayed: True)
		return [
			name			: 'smoke',
			value			: 'clear',
            isStateChange: true,
			descriptionText : descriptionText
		]
	}
}		

def EventOrTest() {
    if (device.currentValue("smoke") == "clear") {
		log.debug "${device.displayName} was tested sucessfully"
        resetSensorTestedDate()
	} else {
		log.debug "${device.displayName} This was not a test - smoke detected !!!"
		sendEvent(name:"smoke", value:"detected")
        sendEvent(name: "lastStatus", value: new Date(), displayed: True)
	}
}
