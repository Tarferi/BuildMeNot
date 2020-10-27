var CommonPromise = function() {
	var self = this;
	self.finished = false;
	self.finishedData = null;
	self.finishedOK = false;
	
	self.callbacks = [];
	
	self.then = function(callbackOK, callbackFail) {
		if(self.finished) {
			if(self.finishedOK) {
				callbackOK(self.finishedData);
			} else {
				callbackFail(self.finishedData);
			} 
		} else {
			self.callbacks.push([callbackOK, callbackFail]);
		}
		return self;
	}
	
	self.link = function(anotherPromise) {
		anotherPromise.then(self.doOK, self.doFail);
	}
	
	self.doFail = function(data) {
		self.finished = true;
		self.finishedOK = false;
		for(var i = 0; i < self.callbacks.length; i++) {
			self.callbacks[i][1](data);
		}
	}
	
	self.doOK = function(data) {
		self.finished = true;
		self.finishedOK = true;
		for(var i = 0; i < self.callbacks.length; i++) {
			self.callbacks[i][0](data);
		}
	}
	
	return this;
}

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
	
	self.showError = function(title, message, showBtnOK, detailsToShow) {
		var errorUI = {
			"type": "div",
			"style": "position: fixed; left: calc(50% - 150px); top: 80px; width: 300px; height: 200px; display: block; padding: 0px; margin: 0px; border: 2px solid black; background: #ffaaaa;",
			"contents": [
				{
					"type": "div",
					"id": "pnl_title",
					"style": "display: block;width:100%;margin:0px;border-bottom: 2px solid black;height: 30px; background: #ff8888;cursor: default;",
					"contents": [
						{
							"type": "span",
							"style": "padding-left: 10px;display: inline-block;margin-top: 5px; margin-bottom: 5px;font-family: Verdana; font-size: 12pt;",
							"id": "titlePnl"
						},
						{
							"type": "span",
							"style": ";display: inline-block;float: right;padding-right: 5px; border-left: 2px solid black;line-height: 30px; padding-left: 5px",
							"innerHTML":"v1"
						}
					]
				},
				{
					"type": "div",
					"style": "display: block; width: 100%; height: calc(100% - 32px);",
					"contents": [
						{
							"type": "textarea",
							"id": "txtMessage",
							"style": "min-width: 250px; min-height: 120px; width: calc(100% - 6px) ; height: calc(100% - 41px); resize: both; outline: none; border: 0px;text-align: center;font-family: Verdana; font-size: 12pt; background: transparent",
							"innerHTML": message
						},
						{
							"type": "div",
							"style": "height: 35px;line-height: 35px; padding-left: 5px; border-top: 2px solid black",
							"contents": [
								{
									"type": "span",
									"id": "pnlOKPnl"
								},
								{
									"type": "button",
									"id": "btnOK",
									"style": "float: right; margin-top: 5px; margin-right: 6px;width: 80px",
									"innerHTML": "OK"
								},
								{
									"type": "button",
									"id": "btnDetails",
									"style": "float: right; margin-top: 5px; margin-right: 6px;width: 80px",
									"innerHTML": "Detaily"
								}
							]
						}
					]
				}
			]
		};
		
		
		var p = new CommonPromise();
		
		var d = self.reconstructUI(errorUI);
		
		var node = d[0];
		var ids = d[1];
		
		ids.titlePnl.innerHTML = title;
		ids.txtMessage.readOnly = true;
		
		if(showBtnOK === true) {
			ids.pnlOKPnl.display = "none";
			ids.btnOK.addEventListener("click", function(){
				document.body.removeChild(node);
				document.body.removeChild(el);
				p.doOK()
			});
			
		} else {
			ids.pnlOKPnl.innerHTML = "Nelze pokračovat"	
			ids.btnOK.style.display = "none";
		}
		if(detailsToShow === undefined) {
			ids.btnDetails.style.display = "none";
		} else {
			var tabStr = function(str) {
				if(str.stack) {
					str = str.stack;
				} else if(str.toString) {
					str = str.toString();
				}
				return str.split("\n").map(function(x){return "\t"+x}).join("\n");
			}
			var showingDetails = false;
			ids.btnDetails.addEventListener("click", function(){
				if(showingDetails){
					showingDetails = false;
					ids.btnDetails.innerHTML = "Detaily";
					ids.txtMessage.innerHTML = message;
					ids.txtMessage.style.textAlign = "center";
				} else {
					showingDetails = true;
					ids.btnDetails.innerHTML = "Skrýt";
					ids.txtMessage.innerHTML = "Popis chyby:\r\n"+tabStr(message) + "\r\n\r\nDetaily:\r\n" + tabStr(detailsToShow);
					ids.txtMessage.style.textAlign = "left";
				}
			});
		}
		
		var el = document.createElement("div");
		el.style.position = "fixed";
		el.style.left = "0px";
		el.style.right = "0px";
		el.style.top = "0px";
		el.style.bottom = "0px";
		el.style.backgroundColor = "#00000044";
		
		document.body.appendChild(el);
		document.body.appendChild(node);
		
		// Moving
		{
			var setPos = function(node, pos, getterAttr, setterAttr) {
				node.style[setterAttr] = pos + "px";
				var ns = node[getterAttr];
				node.style[setterAttr] = (pos - (ns - pos)) + "px";
			}
			
			var mdMove = false;
			var originalNodePosition = null;
			var originalPointerPosition = null;
			ids.pnl_title.addEventListener("mousedown", function(e) {
				mdMove = true;
				originalNodePosition = [node.offsetLeft, node.offsetTop];
				originalPointerPosition = [e.clientX, e.clientY];
			});
			node.addEventListener("mouseup", function() {
				mdMove = false;
			});
			el.addEventListener("mouseup", function() {
				mdMove = false;
			})
			var fnMove = function(e){
				if(mdMove) {
					var newCursorPoisition = [e.clientX, e.clientY];
					
					var newPosX = originalNodePosition[0] + (newCursorPoisition[0] - originalPointerPosition[0]);
					var newPosY= originalNodePosition[1] + (newCursorPoisition[1] - originalPointerPosition[1]);
					
					if(newPosX < 0) {
						newPosX = 0;
					} else if(newPosX + node.offsetWidth > el.offsetWidth) {
						newPosX = el.offsetWidth - node.offsetWidth;
					}
					if(newPosY < 0) {
						newPosY = 0;
					} else if(newPosY + node.offsetHeight > el.offsetHeight) {
						newPosY = el.offsetHeight - node.offsetHeight;
					}
					
					setPos(node, newPosX, "offsetLeft", "left");
					setPos(node, newPosY, "offsetTop", "top");
					
				}
			};
			el.addEventListener("mousemove", fnMove)
			node.addEventListener("mousemove", fnMove)
		}
		
		
		// Resizing
		{	
			var mdSize = false;
			var originalSizesNode = null;
			var originalSizesArea = null;
			var lastAreaSize = [ids.txtMessage.offsetWidth, ids.txtMessage.offsetHeight];
			
			ids.txtMessage.addEventListener("mousedown", function() {
				mdSize = true;
			});
			ids.txtMessage.addEventListener("mouseup", function() {
				mdSize = false;
			});
			
			var moveFN = function() {
				
				var setDim = function(node, size, getterAttr, setterAttr) {
					node.style[setterAttr] = size + "px";
					var ns = node[getterAttr];
					node.style[setterAttr] = (size - (ns -size)) + "px";
				}
				
				if(mdSize) {
					if(originalSizesArea === null) {
						 originalSizesArea = [ids.txtMessage.offsetWidth, ids.txtMessage.offsetHeight];
						 originalSizesNode = [node.offsetWidth, node.offsetHeight];
					}
					var newAreaSizes = [ids.txtMessage.offsetWidth, ids.txtMessage.offsetHeight];
					if(newAreaSizes[0] != lastAreaSize[0] || newAreaSizes[1] != lastAreaSize[1] ) {
						lastAreaSize = newAreaSizes;
						var newWidth = originalSizesNode[0] + (lastAreaSize[0] - originalSizesArea[0]);
						var newHeight = originalSizesNode[1] + (lastAreaSize[1] - originalSizesArea[1]);
						setDim(node, newWidth, "offsetWidth", "width");
						setDim(node, newHeight, "offsetHeight", "height");
					}
				}
			};
			el.addEventListener("mousemove", moveFN);
			node.addEventListener("mousemove", moveFN);
			ids.txtMessage.addEventListener("mousemove", moveFN);
		}
		
		return p;
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
	
	self.JSONstringify = function(data) {
		if (!self.safeCodes) {
			var safe = [];
			safe.push([ 'a', 'z' ]);
			safe.push([ 'A', 'Z' ]);
			safe.push([ '9', '9' ]);
			safe.push(' <>@&#:.;=()');
			self.safeCodes = [];
			for (var i = 0; i < safe.legnth; i++) {
				var d = safe[i];
				if (d.length == 2) {
					var from = d[0].charCodeAt(0);
					var to = d[1].charCodeAt(0);
					for (var x = from; x <= to; x++) {
						self.safeCodes.push(x);
					}
				} else {
					for (var x = 0; x < d.length; x++) {
						self.safeCodes.push(d.charCodeAt(i));
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
				if (self.safeCodes.includes(xc)) {
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
					res.push(self.JSONstringify(key) + ":"  + self.JSONstringify(data[key]));
				}
			}
			return "{" + res.join(",") + "}";
		} else if (typeof (data) == typeof ([])) {
			var res = [];
			for (var i = 0; i < data.length; i++) {
				res.push(self.JSONstringify(data[i]));
			}
			return "[" + res.join(",") + "]";
		} else {
			return JSON.stringify(data);
		}
	}
	
	self.async = function(data, callbackOK, callbackFail, parseResult) {
		var promise = new CommonPromise();
		promise.then(callbackOK, callbackFail);
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
								if (jsn.code === 0 && jsn.result !== undefined) {
									var result = parseResult ? JSON.parse(jsn.result) : jsn.result;
									if(result !== undefined) {
										try {
											promise.doOK(result);
										} catch(e) {
											self.showError("Chyba", "Nastala chyba při zpracování příkazu", true, e);
										}
										return;
									} else {
										promise.doFail("Nepodařilo se dekódovat odpověď serveru");	
									}
								} else {
									promise.doFail("Interní chyba serveru" + (jsn.result ? ": "+jsn.result : ""));
								}
							} else {
								promise.doFail("Server odpověděl ve špatném formátu");	
							}
						} else {
							promise.doFail("Nesprávná odpověď serveru");
						}
					}
				}
			};
			http.onerror = function(e) {
				promise.doFail("Nepodařilo se kontaktovat server");
			};
			http.open("POST", url, true);
			http.send(data);
		} catch (e) {
			promise.doFail("Nastala neočekávaná chyba při komunikaci se serverem" + e);
		}
		return promise;
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
			el.rowSpan = data.rowSpan;
		}
		if (data.name) {
			el.name = data.name;
		}
		if (data.style) {
			el.style = data.style;
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
	
	self.convertDateTime = function(value) {
		return self.convertDate(value) + " " +self.convertOnlyTime(value);
	}
	
	self.convertDate = function(value) {
		var dd = function(x) {
			if (x <= 9) {
				return "0" + x
			} else {
				return "" + x;
			}
		}
		value = value * 1;
		var date = new Date(value);
		var parts = [];
		parts.push(dd(date.getDate()));
		parts.push(dd(date.getMonth() + 1));
		parts.push(dd(date.getFullYear()));
	
		var dmy = parts.join(". ");
		return dmy
	};
	
	self.convertOnlyTime = function(value) {
		var dd = function(x) {
			if (x <= 9) {
				return "0" + x
			} else {
				return "" + x;
			}
		}
		value = value * 1;
		var date = new Date(value);
		var parts = [];
		parts.push(dd(date.getHours()));
		parts.push(dd(date.getMinutes()));
		parts.push(dd(date.getSeconds()));
		return parts.join(":");
	}
	
	self.convertTime = function(value) {
		var dd = function(x) {
			if (x <= 9) {
				return "0" + x
			} else {
				return "" + x;
			}
		}
		value = Math.floor((value * 1)/1000); // sec
		var s = value % 60;
		value = Math.floor((value)/60);
		var m = value % 60;
		value = Math.floor((value)/60);
		var h = value; 
		
		var parts = [];
		parts.push(dd(h));
		parts.push(dd(m));
		parts.push(dd(s));

		var tm = parts.join(":");
		return tm
	};

	self.init = function() {
		
	}
		
	this.init();
	window.RionCommon = self;
	return window.RionCommon;
};