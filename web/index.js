
var allTests = [];

function toHex(c) {
	if (c >= 0 && c <= 9) {
		return (c + '0'.charCodeAt(0));
	} else if (c >= 10 && c <= 15) {
		return ((c - 10) + 'a'.charCodeAt(0));
	} else {
		return false;
	}
}

function encode(bdata) {
	result = [];
	for (var i = 0; i < bdata.length; i++) {
		var c = bdata.charCodeAt(i);
		var c1 = toHex(c >> 4);
		var c2 = toHex(c & 15);
		if (c1 === false || c2 === false) {
			return false;
		}
		result[i * 2] = String.fromCharCode(c1);
		result[(i * 2) + 1] = String.fromCharCode(c2);
	}
	return result.join("");

}

function dec(str) {
	res="";
	// Decode UTF-8
	var decTable = {
			
			195: {
				63: 193, // "Á"
				161: 225, // "á"
				169: 233, // "é"
				137: 201, // "É"
				141: 205, // "Í"
				173: 237, // "í"
				147: 211, // "Ó"
				179: 243, // "ó"
				154: 218, // "Ú"
				186: 250, // "ú"
				157: 221, // "Ý"
				189: 253, // "ý"
				132: 196, // "Ä"
				164: 228, // "ä"
				139: 203, // "Ë"
				171: 235, // "ë"
				143: 207, // "Ï"
				175: 239, // "ï"
				150: 214, // "Ö"
				182: 246, // "ö"
				156: 220, // "Ü"
				188: 252, // "ü"
				191: 255 // "ÿ"
			},

			196: {
				140: 268, // "Č"
				141: 269, // "č"
				142: 270, // "Ď"
				143: 271, // "ď"
				155: 283, // "ě"
				154: 282 // "Ě"
			},
			
			197: {
				135: 327, // "Ň"
				63: 328, // "ň"
				63: 344, // "Ř"
				153: 345, // "ř"
				32: 352, // "Š"
				161: 353, // "š"
				164: 356, // "Ť"
				165: 357, // "ť"
				174: 366, // "Ů"
				175: 367, // "ů"
				189: 381, // "Ž"
				190: 382, // "ž"
				184: 376 // "Ÿ"

			}
	};

	for(var i = 0; i < str.length; i++) {
		var x = str.charAt(i);
		var c = str.charCodeAt(i);
		if(i + 1 < str.length) {
			decT = false;
			if(decTable[c]) {
				decT = decTable[c];
			}
			if(decT!==false){
				i++;
				var c = str.charCodeAt(i);
				if(decT[c]){
					x=String.fromCharCode(decT[c]);
				}
			}
		}
		res+=x;
	}
	return res;
}

function fromHex(c) {
	if (c >= '0'.charCodeAt(0) && c <= '9'.charCodeAt(0)) {
		return c - '0'.charCodeAt(0);
	} else if (c >= 'A'.charCodeAt(0) && c <= 'F'.charCodeAt(0)) {
		return 10 + c - 'A';
	} else if (c >= 'a'.charCodeAt(0) && c <= 'f'.charCodeAt(0)) {
		return 10 + c - 'a'.charCodeAt(0);
	} else {
		return false;
	}
}

function decode(data) {
	sb = "";
	for (var i = 0; i < data.length / 2; i++) {
		var c1 = data.charCodeAt(i * 2);
		var c2 = data.charCodeAt((i * 2) + 1);
		c1 = fromHex(c1);
		c2 = fromHex(c2);
		if (c1 === false || c2 === false) {
			return false;
		}
		var x = (c1 << 4) | c2;
		sb += String.fromCharCode(x);
	}
	return sb;
}

function testI(data) {
	this.data = data;
	var self = this;
	
	this.ta = null;
	this.b1 = null;
	this.rs = null;
	
	this.setComponentsEnabled  = function(enabled) {
		self.ta.readOnly = !enabled;
		self.b1.disabled = !enabled;
	}
	
	this.getID = function() {
		return self.data.id;
	}
	
	this.setResult = function(resultText) {
		this.rs.innerHTML = resultText;
	}
	
	this.getASM = function() {
		return self.ta.value;
	}
	
	this.setSolution = function(data) {
		this.rs.innerHTML = data;
	}
	
	var getNewElement2 = function(parent, tagName, className, contents) {
		var wr = document.createElement(tagName);
		if (className !== false) {
			if(typeof(className) == typeof("str")){
				wr.classList.add(className);
			} else if(typeof(className) == typeof([])){
				for(var x = 0; x < className.length;x++) {
					wr.classList.add(className[x]);
				}
			}
		}
		if(contents!==false){
			wr.innerHTML = contents;
		}
		parent.appendChild(wr);
		return wr;
	}
	
	var getNewElement1 = function(parent, tagName, className) {
		return getNewElement2(parent, tagName, className, false);
	}
	
	var getNewElement0 = function(parent, tagName) {
		return getNewElement1(parent, tagName, false);
	}
	
	var setup = function() {
		// Mame vytvorenu strukturu a ulozeny jednotlive komponenty, nasadime callbacky
		self.b1.addEventListener("click", function() {runTest(self);});
		
		var cancF = function(event){
			if(event.keyCode===9){
				var v=this.value
				var s=this.selectionStart
				var e=this.selectionEnd;
				this.value=v.substring(0, s)+'\t'+v.substring(e);
				this.selectionStart=this.selectionEnd=s+1;
				event.preventDefault();
				return false;
			}
		}
		
		self.ta.addEventListener("keydown", cancF);
	}
	
	this.getElement = function() {
		var el = document.createElement("div"); // Hlavni prvek
		el.classList.add("txtTest");
		
		
		getNewElement2(el, "div", "txtTestLbl", data.title);
		
		var wr = getNewElement1(el, "div", "txtWrap");
		
		self.ta = getNewElement2(wr, "textarea", "txtAsm", data.init);
		
		var pc = getNewElement1(wr, "div", "pnlControls");
		self.b1 = getNewElement2(pc, "button", false, "Otestovat řešení");
			

		// Zadani
		var pz = getNewElement1(el, "div", "txtDescr");
		getNewElement2(pz, "div", "txtDescrName", "Popis");
		getNewElement2(pz, "span", "txtDescrTxt", data.zadani)
		
		// Reseni
		var sl = getNewElement1(el, "div", "txtDescr");
		getNewElement2(sl, "div", ["txtDescrName", "txtDescrNameSolution"], "&#344;ešení");
		self.rs = getNewElement2(sl, "span", "txtDescrSolutionLog", "Zde se objevi detaily testů tvého řešení")
		setup();
		return el;
	};
	
	return this;
}

function async(data, callbackOK, callbackFail) {
	var http = new XMLHttpRequest();
	var url = window.location.href+"test";
	http.open("POST", url, true);
	http.onreadystatechange = function(e) {
		if(e.target.readyState == 4) {
			if(e.target.status == 200) {
				callbackOK(http.responseText);
			} else {
				callbackFail(getErrorSolution(0))
			}
		}
	};
	http.send(data);
}

function submit(id, asm, cbOK, cbFail) {
	var data = {"asm":asm, "id": id}
	var txtEnc = "q=" + encode(JSON.stringify(data));
	async(txtEnc, function(response) {
		var deco=decode(response);
		if(deco!==false) {
			var obj = JSON.parse(deco);
			if(obj!==false){
				if(obj.code == 0){
					cbOK(obj.result);
					return;
				} else {
					cbFail(obj.result);
					return;
				}
			}
			cbFail(getErrorSolution(1));
			return;
		}
		cbFail(getErrorSolution(0));
	}, cbFail);
}

function getErrorSolution(code) {
	if(code == 0){
		return dec("<span class=\"log_err\">Nepodařilo se kontaktovat sestavovací server</span>");
	} else if(code == 1){
		return dec("<span class=\"log_err\">Nepodařilo se dekódovat odpověď sestavovacího serveru</span>");
	} else {
		return dec("<span class=\"log_err\">Neznámá chyba</span>");
	}
}

function setAllTestsEnabled(enabled) {
	for (var i = 0; i < allTests.length; i++) {
		allTests[i].setComponentsEnabled(enabled);
	}
}

function runTest(tesx) {
	setAllTestsEnabled(false);
	var cbOK = function(data) {
		tesx.setSolution(data);
		setAllTestsEnabled(true);	
	};
	
	var cbFail = function(descr) {
		descr = dec(descr);
		tesx.setSolution(descr);
		setAllTestsEnabled(true);
	}
	tesx.setSolution(dec("Vyhodnocuji test..."));
	submit(tesx.getID(), tesx.getASM(), cbOK, cbFail);
}

function materialize(data) {
	id_indiv.innerHTML = "";
	allTests = [];
	for(var i = 0; i <data.length; i++) {
		data[i].title = dec(data[i].title);
		data[i].id = dec(data[i].id);
		data[i].zadani = dec(data[i].zadani);
		data[i].init = dec(data[i].init);
		var dataI = new testI(data[i]);
		allTests[allTests.length] = dataI;
		id_indiv.appendChild(dataI.getElement());
	}
}

function loadRemoteTests() {
	var cbFail = function(data) {
		id_loader.innerHTML = dec(data);
		id_loader.classList.remove("loader");
		id_loader.classList.add("loader_error");
	};
	var cbOk = function(data) {
		var deco = decode(data);
		if(deco!==false){
			var jsn = JSON.parse(deco);
			if(jsn!==false){
				if(jsn.code === 0) {
					materialize(jsn.tests);		
				} else {
					cbFail(dec("Nepodařilo se nahrát testy: <br />" + jsn.result));
				}
				return;
			}
		}
		cbFail(dec("Nepodařilo se dekódovat testy"));
	};
	var data = {"action":"COLLECT"}
	var txtEnc = "q=" + encode(JSON.stringify(data));
	async(txtEnc, cbOk, cbFail);
}

function aload() {
	loadRemoteTests();
}
