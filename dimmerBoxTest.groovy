/*
*/
def driverVer() { return "TEST" }
metadata {
	definition (name: "dimmerBox",
				namespace: "davegut",
				author: "Dave Gutheinz") {
		capability "Refresh"
	}
	tiles(scale: 2) {
		standardTile("refresh", "capability.refresh", width: 2, height: 2, decoration: "flat") {
			state "default", label: "Refresh", action: "refresh.refresh"
		}
		main("refresh")
		details("refresh")
	}
	preferences {
		input ("device_IP", "text", title: "Manual Install Device IP")
	}
}
def installed() { updated() }
def updated() {
	unschedule()
	if (!device_IP) {
		logWarn("updated:  deviceIP  is not set.")
		return
	}
	updateDataValue("deviceIP", device_IP)
	logInfo("Device IP set to ${getDataValue("deviceIP")}")
	updateDataValue("driverVersion", driverVer())
	refresh()
}


//	===== Device Commands and Parse	=====
def refresh() {
	logDebug("SPECIAL REFRESH")
    def host = "${getDataValue("deviceIP")}:80"
    def action = "specialParse"
	List actions = []
    actions.add(new physicalgraph.device.HubAction([method: "GET", path: "/api/dimmer/state", 
        		headers: [Host: host]], null, [callback: action]))
    actions.add(new physicalgraph.device.HubAction([method: "GET", path: "/api/device/state", 
        		headers: [Host: host]], null, [callback: action]))
    actions.add(new physicalgraph.device.HubAction([method: "POST", path: "/api/dimmer/set",
    			body: """{"dimmer":{"desiredBrightness":255,"fadeSpeed":213}}""",
                headers: [Host: host]], null, [callback: action]))
    actions.add(new physicalgraph.device.HubAction([method: "POST", path: "/api/dimmer/set",
    			body: """{"dimmer":{"desiredBrightness":127,"fadeSpeed":255}}""",
                headers: [Host: host]], null, [callback: action]))
    actions.add(new physicalgraph.device.HubAction([method: "POST", path: "/api/dimmer/set",
    			body: """{"dimmer":{"desiredBrightness":0,"fadeSpeed":213}}""",
                headers: [Host: host]], null, [callback: action]))
    actions.add(new physicalgraph.device.HubAction([method: "GET", path: "/api/device/uptime", 
        		headers: [Host: host]], null, [callback: action]))
    actions.add(new physicalgraph.device.HubAction([method: "GET", path: "/api/wifi/status", 
        		headers: [Host: host]], null, [callback: action]))
    sendHubCommand(actions, 5000)
}
def specialParse(response) {
	if(response.status != 200 || response.body == null) {
		logWarn("parseInput: Command generated an error return: ${response.status} / ${response.body}")
		return
	}
	def jsonSlurper = new groovy.json.JsonSlurper()
	def cmdResponse = jsonSlurper.parseText(response.body)
	logDebug("specialParse: response = ${cmdResponse}")
}

//	===== Utility Methods =====
def logInfo(msg) {
	log.info "${device.label} ${driverVer()}:   ${msg}"
}
def logDebug(msg){
	log.debug "${device.label} ${driverVer()}:   ${msg}"
}
def logWarn(msg){ log.warn "${device.label} ${driverVer()}:    ${msg}" }

//	end-of-file