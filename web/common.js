var Common = function() {
	if(window.RionCommon) {
		return window.RionCommon;
	}
	var self = this;
	self.hasLoginPanel = false;
	self.identity = $IDENTITY_TOKEN$;

	var REMOTE_NOW = $REMOTE_NOW$;
	var now = new Date().getTime();
	
	var diff = REMOTE_NOW - now;
	
	self.getRemoteNow = function() {
		return new Date().getTime() + diff;
	}
	
	self.logout = function() {
	    var cookies = document.cookie.split(";");
	    for (var i = 0; i < cookies.length; i++) {
	        var cookie = cookies[i];
	        var eqPos = cookie.indexOf("=");
	        var name = eqPos > -1 ? cookie.substr(0, eqPos) : cookie;
	        document.cookie = name + "=;expires=Thu, 01 Jan 1970 00:00:00 GMT";
	    }
	    window.location.href="http://" + self.TOOLCHAIN +".rion.cz/logout"
	}
	
	self.sanitizeData = function(data) {
		if(data instanceof Array) { 
			var res = [];
			for(var i = 0; i < data.length; i++) {
				res[i] = self.sanitizeData(data[i]); 
			}
			return res;
		} else if(data instanceof Object) { 
			var res = {};
			for(var item in data){
				if(data.hasOwnProperty(item)){
					res[self.sanitizeData(item)] = self.sanitizeData(data[item]);
				}
			}
			return res;
		} else if(typeof(data) === typeof("abc")){
			return data;
		} else {
			return data;
		}
	}
	
	self.headerPanelUI = {
	  "type": "div",
	  "id": "txtHeader",
	  "contents": [
	    {
	      "type": "span",
	      "contents": [
	        {
	          "type": "span",
	          "id": "txtLogin",
	        }
	      ],
	      "class": "txtHeader1"
	    },
	    {
	      "type": "button",
	      "id": "btnLogout",
	      "innerHTML": "Odhlásit"
	    }
	  ],
	  "class": "txtHeader"
	}
	
	self.JSONstringify = function(data, safeCodes) {
		if (!safeCodes) {
			var safe = [];
			safe.push([ 'a', 'z' ]);
			safe.push([ 'A', 'Z' ]);
			safe.push([ '9', '9' ]);
			safe.push(' <>@&#:.;=()');
			var safeCodes = [];
			for (var i = 0; i < safe.legnth; i++) {
				var d = safe[i];
				if (d.length == 2) {
					from = d[0].charCodeAt(0);
					to = d[1].charCodeAt(0);
					for (var x = from; x <= to; x++) {
						safeCodes.push(x);
					}
				} else {
					for (var x = 0; x < d.length; x++) {
						safeCodes.push(d.charCodeAt(i));
					}
				}
			}
		}
	
		var esc = function(x) {
			x = "0000" + x.toString(16);
			return "\\u" + x.substr(-4);
		}
	
		if (typeof (data) == typeof ("str")) {
			var n = [];
			for (var i = 0; i < data.length; i++) {
				var xc = data.charCodeAt(i);
				if (safeCodes.includes(xc)) {
					n.push(data.charAt(i))
				} else {
					n.push(esc(xc));
				}
			}
			return "\"" + n.join("") + "\"";
		} else if (typeof (data) == typeof ({}) && !data.hasOwnProperty("length")) {
			var res = [];
			for ( var key in data) {
				if (data.hasOwnProperty(key)) {
					res.push(self.JSONstringify(key, safeCodes) + ":"  + self.JSONstringify(data[key], safeCodes));
				}
			}
			return "{" + res.join(",") + "}";
		} else if (typeof (data) == typeof ([])) {
			var res = [];
			for (var i = 0; i < data.length; i++) {
				res.push(self.JSONstringify(data[i], safeCodes));
			}
			return "[" + res.join(",") + "]";
		} else {
			return JSON.stringify(data, safeCodes);
		}
	}
	
	self.async = function(data, callbackOK, callbackFail, parseResult) {
		if(parseResult === undefined) {
			parseResult = true;
		}
		data = self.JSONstringify(data);
		var http = new XMLHttpRequest();
		try {
			var url = window.location.href + "exec?cache=" + self.generateRandomString(10);
			http.onreadystatechange = function(e) {
				if (e.target.readyState == 4) {
					if (e.target.status == 200) {
						var deco = http.responseText;
						if (deco !== false) {
							var jsn = JSON.parse(deco);
							if (jsn !== false) {
								if (jsn.code === 0 && jsn.result) {
									var result = parseResult ? JSON.parse(jsn.result) : jsn.result;
									if(result) {
										callbackOK(result);
										return;
									}
								}
							}
						}
						callbackFail(self.getErrorSolution(0));
					}
				}
			};
			http.onerror = function(e) {
				callbackFail(self.getErrorSolution(0));
			};
			http.open("POST", url, true);
			http.send(data);
		} catch (e) {
			callbackFail(self.getErrorSolution(0));
		}
	}

	self.generateRandomString = function(length) {
		var result = "";
		var characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		var charactersLength = characters.length;
		for (var i = 0; i < length; i++) {
			result += characters.charAt(Math.floor(Math.random() * charactersLength));
		}
		return result;
	}

	self.getErrorSolution = function(code) {
		if (code == 0) {
			return ("<span class=\"log_err\">Nepodařilo se kontaktovat server</span>");
		} else if (code == 1) {
			return ("<span class=\"log_err\">Nepodařilo se dekódovat odpověď serveru</span>");
		} else if (code == 3) {
			return ("<span class=\"log_err\">Nepodařilo se kontaktovar server. Obnovte prosím stránku</span>");
		} else if (code == 53) {
			return ("<span class=\"log_err\">Byl jsi odhlášen. Pro přihlášení si obnov stránku (nezapomeň si někam bokem uložit kód, který se právě snažíš přeložit)</span>");
		} else {
			return ("<span class=\"log_err\">Neznámá chyba</span>");
		}
	}
	
	self.addLoginPanel = function() {
		if(!self.hasLoginPanel) {
			self.hasLoginPanel = true;
			var data = self.reconstructUI(self.headerPanelUI);
			self.loginPnl = data[0];
			var ids = data[1];
			self.btnLogout = ids.btnLogout;
			document.body.appendChild(self.loginPnl);
			ids.txtLogin.innerHTML = self.identity.name + " ("+self.identity.primary+"@"+self.identity.group+")&nbsp;";
			document.body.style.marginTop = "60px";
			self.loginPnl.style.display = self.loginPanelVisible ? "" : "none";
		}
	}
	
	self.loginPanelVisible = false;
	self.setLoginPanelVisible = function(vis) {
		self.loginPanelVisible = vis;
		if(self.loginPnl) {
			self.loginPnl.style.display = self.loginPanelVisible ? "" : "none";
		} 
	}
	
	self.activeInitLoader = undefined;
	
	self.showInitLoader = function(title, color) {
		if(self.activeInitLoader === undefined) {
			self.activeInitLoader = document.createElement("div");
			self.activeInitLoader.style.position = "fixed";
			self.activeInitLoader.style.left = "0px";
			self.activeInitLoader.style.right = "0px";
			self.activeInitLoader.style.top = "0px";
			self.activeInitLoader.style.bottom = "0px";
			self.activeInitLoader.style.background = "#ffffff";
			
			var chd = document.createElement("div");
			chd.style.position = "fixed";
			chd.style.left = "calc(50% - 120px)";
			chd.style.top = "calc(50% - 80px)";
			
			chd.style.width = "120px";
			chd.style.padding = "80px";
			chd.style.display = "block";
			chd.style.textAlign = "center";
			
			self.activeInitLoader.appendChild(chd);
			
			document.body.appendChild(self.activeInitLoader);
		}	
		self.activeInitLoader.children[0].style.border = "5px solid " + color;
		self.activeInitLoader.children[0].innerHTML = title;
	}
	
	self.hideInitLoader = function() {
		if(self.activeInitLoader !== undefined) {
			document.body.removeChild(self.activeInitLoader);
			self.activeInitLoader = undefined;
		}
	}
	
	self.activeLoader = undefined;
	
	self.showLoader = function() {
		if(self.activeLoader === undefined) {
			self.activeLoader = document.createElement("div");
			self.activeLoader.style.position = "fixed";
			self.activeLoader.style.left = "0px";
			self.activeLoader.style.right = "0px";
			self.activeLoader.style.top = "0px";
			self.activeLoader.style.bottom = "0px";
			self.activeLoader.style.background = "#00000066";
			document.body.appendChild(self.activeLoader);
		}
		self.activeLoader.style.display = "block";
	}
	
	self.hideLoader = function() {
		if(self.activeLoader !== undefined) {
			self.activeLoader.style.display = "none";
		}
	}
	
	self.addButtonToLoginPanel = function(name, callback) {
		var btn = document.createElement("button");
		btn.innerHTML = name;
		self.addLoginPanel();
		self.loginPnl.removeChild(self.btnLogout);
		self.loginPnl.appendChild(btn);
		self.loginPnl.appendChild(self.btnLogout);
		btn.addEventListener("click", callback);
		return btn;
	}
	
	self.reconstructUI = function(data, ids) {
		if (!ids) {
			ids = {};
		}
		var type = data.type;
		var baseType = type == "checkbox" ? "input" : type
		var el = document.createElement(baseType);
		if(type != baseType) {
			el.type = type;
		}
		if(data.readonly) {
			el.onclick = function(){return false;};
		}
		if (data.class) {
			if (typeof (data.class) == typeof ("str")) {
				var dataclass = data.class.split(" ");
				for(var i = 0; i < dataclass.length;i++) {
					el.classList.add(dataclass[i]);
				}
			} else if (typeof (data.class) == typeof ([])) {
				for (var x = 0; x < data.class.length; x++) {
					el.classList.add(data.class[x]);
				}
			}
		}
		if (data.innerHTML) {
			el.innerHTML = data.innerHTML;
		}
		if (data.colSpan) {
			el.colSpan = data.colSpan;
		}
		if (data.rowSpan) {
			el.rowSpan = data.colspan;
		}
		if (data.name) {
			el.name = data.name;
		}
		if (data.value) {
			el.value = data.value
		}
		if (data.id) {
			ids[data.id] = el;
		}
		if (data.contents) {
			for (var i = 0; i < data.contents.length; i++) {
				var childData = data.contents[i];
				var sub = self.reconstructUI(childData, ids);
				var subEl = sub[0];
				var subIds = sub[1];
				el.appendChild(subEl);
				for (var x in subIds) {
					ids[x] = subIds[x];
				}
			}
		}
		return [el, ids];
	}

	self.init = function() {
		
	}
		
	this.init();
	window.RionCommon = self;
	return window.RionCommon;
};