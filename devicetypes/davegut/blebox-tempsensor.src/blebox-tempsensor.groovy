/*
===== Blebox Hubitat Integration Driver

	Copyright 2019, Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER: The author of this integration is not associated with blebox.  This code uses the blebox
open API documentation for development and is intended for integration into the Hubitat Environment.

===== Hiatory =====
08.22.19	1.0.01	Initial release.
*/
//	===== Definitions, Installation and Updates =====
def driverVer() { return "1.0.01" }

metadata {
	definition (name: "bleBox tempSensor",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.thermostat") {
		capability "Temperature Measurement"
		attribute "trend", "string"
		attribute "sensorHealth", "string"
        capability "Actuator"
        capability "Health Check"
	}
	tiles(scale: 2) {
	   	multiAttributeTile(name:"temperature", type:"device.temperature", width:6, height:4, canChangeIcon: true) {
    		tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
	            attributeState("default", label:'${currentValue}°', icon: "st.Weather.weather2", 
                backgroundColor: "#1e9cbb")
            }
            tileAttribute("device.trend", key: "SECONDARY_CONTROL") {
	            attributeState("default", label: 'Trend: ${currentValue}')
           }
        }
		valueTile("health", "device.sensorHealth", width: 4, height: 2) {
			state "default", label: 'Sensor Health: ${currentValue}'
		}
		standardTile("refresh", "capability.refresh", width: 2, height: 2, decoration: "flat") {
			state "default", label: "Refresh", action: "refresh.refresh"
		}
        main(["temperature"])
        details(["temperature", "health", "refresh"])
    }
	preferences {
		input ("device_IP", "text", title: "Manual Install Device IP")
		input ("tempScale", "enum", title: "Temperature Scale", options: ["C", "F"])
		input ("refreshInterval", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"])
		input ("debug", "bool", title: "Enable debug logging")
		input ("descriptionText", "bool", title: "Enable description text logging")
	}
}

def installed() {
	logInfo("Installing...")
	sendEvent(name: "DeviceWatch-Enroll",value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${device.hub.hardwareID}\"}", displayed: false)
	updated()
}

def updated() {
	logInfo("Updating...")
	unschedule()

	if (!getDataValue("applicationVersion")) {
		if (!device_IP) {
			logWarn("updated:  deviceIP  is not set.")
			return
		}
		updateDataValue("deviceIP", device_IP)
		logInfo("Device IP set to ${getDataValue("deviceIP")}")
		//	Update device name on manual installation to standard name
		sendGetCmd("/api/device/state", "setDeviceName")
	}

	switch(refreshInterval) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		default: runEvery30Minutes(refresh)
	}
	if (tempScale == null) { state.tempScale = "C" }
    else { state.tempScale = tempScale }
	updateDataValue("driverVersion", driverVer())

	refresh()
}

def setDeviceName(response) {
	def cmdResponse = parseInput(response)
	logDebug("setDeviceData: ${cmdResponse}")
	device.setName(cmdResponse.device.type)
	logInfo("setDeviceData: Device Name updated to ${cmdResponse.device.type}")
}


//	===== Commands and Parse Returns =====
def ping() { refresh() }

def refresh() {
	logDebug("refresh.")
	sendGetCmd("/api/tempsensor/state", "commandParse")
}

def commandParse(response) {
	def cmdResponse = parseInput(response)
	logDebug("commandParse: cmdResponse = ${cmdResponse}")

	def respData = cmdResponse.tempSensor.sensors[0]
	def temperature = Math.round(respData.value.toInteger() / 10) / 10
	if (state.tempScale == "F") {
		temperature = Math.round((3200 + 9*respData.value.toInteger() / 5) / 100)
	}
	def trend
	switch(respData.trend) {
		case "1":
			trend = "even"; break
		case "2":
			trend = "down"; break
		case "3":
			trend = "up"; break
		default:
			trend = "No Data"
	}
	def sensorHealth = "normal"
	if (respData.state == "3") {
		sensorHealth = "sensor error"
		logWarn("Sensor Error")
	}
	sendEvent(name: "sensorHealth", value: sensorHealth)
	sendEvent(name: "temperature", value: temperature, unit: tempScale)
	sendEvent(name: "trend", value: trend)
	logInfo("commandParse: Temperature value set to ${temperature}")
}

//	===== Communications =====
private sendGetCmd(command, action){
	logDebug("sendGetCmd: ${command} // ${getDataValue("deviceIP")} // ${action}")
	def parameters = [method: "GET",
    				  path: command,
                      headers: [
                          Host: "${getDataValue("deviceIP")}:80"
                      ]]
	sendHubCommand(new physicalgraph.device.HubAction(parameters, null, [callback: action]))
}
private sendPostCmd(command, body, action){
	logDebug("sendPostCmd: ${command} // ${getDataValue("deviceIP")} // ${action}")
	def parameters = [method: "POST",
					  path: command,
					  body: body,
					  headers: [
						  Host: "${getDataValue("deviceIP")}:80"
					  ]]
	sendHubCommand(new physicalgraph.device.HubAction(parameters, null, [callback: action]))
}
def parseInput(response) {
	try {
		def jsonSlurper = new groovy.json.JsonSlurper()
		return jsonSlurper.parseText(response.body)
	} catch (error) {
		logWarn "CommsError: ${error}."
	}
}


//	===== Utility Methods =====
def logInfo(msg) {
	if (descriptionText != false) { log.info "${device.label} ${driverVer()}:   ${msg}" }
}
def logDebug(msg){
	if(debug == true) { log.debug "${device.label} ${driverVer()}:   ${msg}" }
}
def logWarn(msg){ log.warn "${device.label} ${driverVer()}:    ${msg}" }

//	end-of-file