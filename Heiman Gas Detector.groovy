/**
 *  Heiman Natural Gas Sensor
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
 */


import hubitat.zigbee.clusters.iaszone.ZoneStatus
 
metadata {
	definition (name: "Heiman Gas Detector", namespace: "hubitat", author: "cuboy29", importURL: "https//") {
		
        capability "Configuration"
        capability "SmokeDetector"
        capability "Sensor"
        capability "Refresh"
              
        attribute "smoke", "string"
        
		fingerprint profileID: "0104", deviceID: "12", inClusters: "0000,0003,0500,0009", outClusters: "0003,0019"      
    }
}
 
def parse(String description) {
    
	//log.debug "description: $description"
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
       
            if ((descMap?.cluster == "0500" && descMap.attrInt == 0x0001) && (descMap.value == '002B')){  //Zone Type
                log.debug "Zone Type is Carbon Monoxide (CO) Sensor"
			}else if ((descMap?.cluster == "0500" && descMap.attrInt == 0x0000) && (descMap.value == '01')){  //Zone State
                log.debug "Zone State is enrolled"
			}else if ((descMap?.cluster == "0500" && descMap.attrInt == 0x0002) && ((descMap.value == '0030') || (descMap.value == '0020'))){  //Zone Status
                log.debug "${device.displayName} is cleared"
            }else if (descMap?.cluster == "0000" && descMap.attrInt == 0x0004){  //Manufacture
                sendEvent(name: "manufacture", value: descMap.value)
            }else if (descMap?.cluster == "0000" && descMap.attrInt == 0x0005){  //Model 
                sendEvent(name: "model", value: descMap.value)
            }
	}

	//def result = descMap ? createEvent(descMap) : [:]	
	return descMap   
}    


private Map translateZoneStatus(ZoneStatus zs) {
	// Some sensor models that use this DTH use alarm1 and some use alarm2 to signify motion       
	return getGasResult(zs.isAlarm1Set() || zs.isAlarm2Set())
}

private Map getGasResult(value) {

    def descriptionText = value ? "${device.displayName} has detected GAS!" : "${device.displayName} is clear!"
    log.debug descriptionText
	return [
			name			: 'smoke',
			value			: value ? 'detected' : 'clear',
            isStateChange: true,
			descriptionText : descriptionText
	]
}

private parseCatchAllMessage(String description) {
    
    Map resultMap = [:]
    def descMap = zigbee.parse(description)  
    //log.debug descMap.inspect()
    return resultMap
}

private Map parseIasMessage(String description) {
    
    ZoneStatus zs = zigbee.parseZoneStatus(description)    
    translateZoneStatus(zs)    
}

def refresh() {
    
	log.debug "Refreshing..."
	def refreshCmds = []
    
    refreshCmds +=
	zigbee.readAttribute(0x0500, 1) +	// IAS ZoneType
    zigbee.readAttribute(0x0500, 0) +	// IAS ZoneState
    zigbee.readAttribute(0x0500, 2) +	// IAS ZoneStatus
    zigbee.readAttribute(0x0000, 7) +	// power source
    //zigbee.readAttribute(0x0009, 7) +	// not sure what this cluster do yet
    zigbee.readAttribute(0x0000, 4) +	// manufacture
    zigbee.readAttribute(0x0000, 5) +	// model indentification
        zigbee.enrollResponse()
    
	return refreshCmds
}

def configure() {
    
    log.debug "Configuring..."
    
    def cmds = [
            //bindings
            "zdo bind 0x${device.deviceNetworkId} 1 1 0x0500 {${device.zigbeeId}} {}", "delay 200",
        ] +  zigbee.enrollResponse(1200) + refresh()
    
    return cmds
}
