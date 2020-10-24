
function aload() {
    var jwt_data = "$INJECT_JWT$";
    var contents = document.getElementById("contents");
    const domain = 'tauri.meet.rion.cz';
    const options = {
	    roomName: 'JitsiMeetAPIExample',
	    width: "100%",
	    height: "100%",
 	    parentNode: contents,
		roomName: "TEST",
		jwt: jwt_data
    };
    const api = new JitsiMeetExternalAPI(domain, options);
}
