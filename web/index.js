var confetti={maxCount:150,speed:2,frameInterval:15,alpha:1,gradient:!1,start:null,stop:null,toggle:null,pause:null,resume:null,togglePause:null,remove:null,isPaused:null,isRunning:null};!function(){confetti.start=s,confetti.stop=w,confetti.toggle=function(){e?w():s()},confetti.pause=u,confetti.resume=m,confetti.togglePause=function(){i?m():u()},confetti.isPaused=function(){return i},confetti.remove=function(){stop(),i=!1,a=[]},confetti.isRunning=function(){return e};var t=window.requestAnimationFrame||window.webkitRequestAnimationFrame||window.mozRequestAnimationFrame||window.oRequestAnimationFrame||window.msRequestAnimationFrame,n=["rgba(30,144,255,","rgba(107,142,35,","rgba(255,215,0,","rgba(255,192,203,","rgba(106,90,205,","rgba(173,216,230,","rgba(238,130,238,","rgba(152,251,152,","rgba(70,130,180,","rgba(244,164,96,","rgba(210,105,30,","rgba(220,20,60,"],e=!1,i=!1,o=Date.now(),a=[],r=0,l=null;function d(t,e,i){return t.color=n[Math.random()*n.length|0]+(confetti.alpha+")"),t.color2=n[Math.random()*n.length|0]+(confetti.alpha+")"),t.x=Math.random()*e,t.y=Math.random()*i-i,t.diameter=10*Math.random()+5,t.tilt=10*Math.random()-10,t.tiltAngleIncrement=.07*Math.random()+.05,t.tiltAngle=Math.random()*Math.PI,t}function u(){i=!0}function m(){i=!1,c()}function c(){if(!i)if(0===a.length)l.clearRect(0,0,window.innerWidth,window.innerHeight),null;else{var n=Date.now(),u=n-o;(!t||u>confetti.frameInterval)&&(l.clearRect(0,0,window.innerWidth,window.innerHeight),function(){var t,n=window.innerWidth,i=window.innerHeight;r+=.01;for(var o=0;o<a.length;o++)t=a[o],!e&&t.y<-15?t.y=i+100:(t.tiltAngle+=t.tiltAngleIncrement,t.x+=Math.sin(r)-.5,t.y+=.5*(Math.cos(r)+t.diameter+confetti.speed),t.tilt=15*Math.sin(t.tiltAngle)),(t.x>n+20||t.x<-20||t.y>i)&&(e&&a.length<=confetti.maxCount?d(t,n,i):(a.splice(o,1),o--))}(),function(t){for(var n,e,i,o,r=0;r<a.length;r++){if(n=a[r],t.beginPath(),t.lineWidth=n.diameter,i=n.x+n.tilt,e=i+n.diameter/2,o=n.y+n.tilt+n.diameter/2,confetti.gradient){var l=t.createLinearGradient(e,n.y,i,o);l.addColorStop("0",n.color),l.addColorStop("1.0",n.color2),t.strokeStyle=l}else t.strokeStyle=n.color;t.moveTo(e,n.y),t.lineTo(i,o),t.stroke()}}(l),o=n-u%confetti.frameInterval),requestAnimationFrame(c)}}function s(t,n,o){var r=window.innerWidth,u=window.innerHeight;window.requestAnimationFrame=window.requestAnimationFrame||window.webkitRequestAnimationFrame||window.mozRequestAnimationFrame||window.oRequestAnimationFrame||window.msRequestAnimationFrame||function(t){return window.setTimeout(t,confetti.frameInterval)};var m=document.getElementById("confetti-canvas");null===m?((m=document.createElement("canvas")).setAttribute("id","confetti-canvas"),m.setAttribute("style","display:block;z-index:999999;pointer-events:none;position:fixed;top:0"),document.body.prepend(m),m.width=r,m.height=u,window.addEventListener("resize",function(){m.width=window.innerWidth,m.height=window.innerHeight},!0),l=m.getContext("2d")):null===l&&(l=m.getContext("2d"));var s=confetti.maxCount;if(n)if(o)if(n==o)s=a.length+o;else{if(n>o){var f=n;n=o,o=f}s=a.length+(Math.random()*(o-n)+n|0)}else s=a.length+n;else o&&(s=a.length+o);for(;a.length<s;)a.push(d({},r,u));e=!0,i=!1,c(),t&&window.setTimeout(w,t)}function w(){e=!1}}();

var tester = function() {
	
	var self = this;
	
	self.allTests = [];
	self.mres = function() {
		for(var i = 0; i < self.allTests.length; i++) {
			self.allTests[i].handleResize();
		}
	}
	
	self.IDENTITY_TOKEN = $IDENTITY_TOKEN$;
	
	
	self.toHex = function(c) {
		if (c >= 0 && c <= 9) {
			return (c + '0'.charCodeAt(0));
		} else if (c >= 10 && c <= 15) {
			return ((c - 10) + 'a'.charCodeAt(0));
		} else {
			return false;
		}
	}
	
	self.encode = function(bdata) {
		var result = [];
		for (var i = 0; i < bdata.length; i++) {
			var c = bdata.charCodeAt(i);
			if(c < 256) { // Special chars ignored
				var c1 = self.toHex(c >> 4);
				var c2 = self.toHex(c & 15);
				if (c1 === false || c2 === false) {
					return false;
				}
				result[i * 2] = String.fromCharCode(c1);
				result[(i * 2) + 1] = String.fromCharCode(c2);
			}
		}
		return result.join("");
	
	}
	
	self.dec = function(str) {
		var res="";
		// Decode UTF-8
		var decTable = {
				
				195: {
					63: 193, // "??"
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
					132: 328, // "??"
					63: 344, // "??"
					153: 345, // "ř"
					32: 352, // "Š"
					161: 353, // "š"
					164: 356, // ""
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
				var decT = false;
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
	
	self.fromHex = function(c) {
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
	
	self.decode = function(data) {
		var sb = "";
		for (var i = 0; i < data.length / 2; i++) {
			var c1 = data.charCodeAt(i * 2);
			var c2 = data.charCodeAt((i * 2) + 1);
			c1 = self.fromHex(c1);
			c2 = self.fromHex(c2);
			if (c1 === false || c2 === false) {
				return false;
			}
			var x = (c1 << 4) | c2;
			sb += String.fromCharCode(x);
		}
		return sb;
	}
	
	self.reconstructUI = function(data, ids) {
		if(!ids){
			ids = {};
		}
		var type = data.type;
		var el = document.createElement(type);
		if(data.class) {
			el.classList.add(data.class);
		}
		if(data.innerHTML) {
			el.innerHTML = data.innerHTML;
		}
		if(data.colSpan){
			el.colSpan = data.colSpan;
		}
		if(data.rowSpan){
			el.rowSpan = data.colspan;
		}
		if(data.id) {
			el.id = data.id;
			ids[data.id] = el;
		}
		if(data.contents) { 
			for(var i = 0; i < data.contents.length; i++) {
				var childData = data.contents[i];
				var sub = self.reconstructUI(childData, ids);
				var subEl = sub[0];
				var subIds = sub[1];
				el.appendChild(subEl);
				for(var x in subIds){
					ids[x] = subIds[x];
				}
			}
		}
		return [el, ids];
	}
	
	self.testI = function(data, tester) {
		this.data = data;
		var self = this;
		var tester = tester;
		
		this.ta = null;
		this.b1 = null;
		this.rs = null;
		
		var UI = {
			      "type":"div",
			      "class":"w0",
			      "contents":[
			         {
			            "type":"div",
			            "class":"w00",
			            "id":"nwBorder",
			            "contents": [
			            	{
			            		"type":"div",
			            		"class":"w001",
			            		"id":"txtBrief"
			            	},{
			            		"type":"div",
			            		"class":"w002",
			            		"contents":[
			            			{
				            			"type":"Button",
					            		"innerHTML":"Skrýt",
					            		"id":"btnHide"
			            			}
			            		]
			            	}
			            ]
			         },
			         {
			            "type":"div",
			            "id":"pnlMain",
			            "class":"w1",
			            "contents":[
			               {
			                  "type":"div",
			                  "class":"w11",
			                  "contents":[
			                     {
			                        "type":"textarea",
			                        "class":"w111",
			                        "id":"txtArea"
			                     },
			                     {
			                        "type":"div",
			                        "class":"w112",
			                        "contents":[
			                           {
			                              "type":"button",
			                              "id":"runtests",
			                              "innerHTML":"Otestovat řešení"
			                           },
			                           {
			                        	   "type":"div",
			                        	   "class":"w1121"
			                           }
			                        ]
			                     }
			                  ]
			               },
			               {
			                  "type":"div",
			                  "class":"w12",
			                  "contents":[
			                     {
			                        "type":"div",
			                        "class":"w121",
			                        "innerHTML":"Zadání"
			                     },
			                     {
			                        "type":"div",
			                        "id":"txtDescr",
			                        "class":"w122"
			                     }
			                  ]
			               },
			               {
			                  "type":"div",
			                  "class":"w13",
			                  "contents":[
			                     {
			                        "type":"div",
			                        "class":"w131",
			                        "innerHTML":"Řešení"
			                     },
			                     {
			                        "type":"div",
			                        "id":"txtSolution",
			                        "class":"w132"
			                     }
			                  ]
			               }
			            ]
			         }
			      ]
				};
	
		this.handleResize = function() {
			var parentHeight = self.ta.parentElement.clientHeight;
			var parentWidth = self.ta.parentElement.offsetWidth;
			
			var paddingBottom = 45;
			parentHeight -= paddingBottom;
			
			// Handle text area width
			self.ta.style.width = parentWidth+"px";
			var currentWidth = self.ta.offsetWidth;
			var fixedWidth = parentWidth - (currentWidth - parentWidth);
			self.ta.style.width = fixedWidth+"px";
			
			// Handle text area height
			self.ta.style.height = parentHeight+"px";
			var currentHeight = self.ta.clientHeight;
			var fixedHeight = parentHeight - (currentHeight - parentHeight);
			self.ta.style.height = fixedHeight +"px";
			
		}
		
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
			
			if(self.btnHide) {
				
				var clickCB = function() {
					if(self.isCollapsed()) {
						self.setCollapsed(false);
					} else {
						self.setCollapsed(true);
					}
				};
				
				self.btnHide.addEventListener("click", clickCB);
			}
		}
		
		this.setFinished = function(collapse) {
			if(collapse) {
				self.setCollapsed(true);
			}
			if(self.nwBorder){
				self.nwBorder.style.background = "#68CD34";
			}
		}
		
		this.isCollapsed = function() {
			return self.mainPnl.style.display == "none";
		}
		
		this.setCollapsed = function(collapsed) {
			self.mainPnl.style.display = collapsed ?  "none" : "table";
			if(self.btnHide){
				self.btnHide.innerHTML = collapsed ? "Zobrazit" : "Skrýt";
			}
			if(self.nwBorder){
				self.nwBorder.style.borderLeft = collapsed ? "1px solid black" : "";
				self.nwBorder.style.borderRight = collapsed ? "1px solid black" : "";
			}
			tester.mres();
		}
		
		this.getElement = function() {
			var struct = tester.reconstructUI(UI);
			var el = struct[0];
			var ids = struct[1];
			var solvStr = data.finished_date ? " (vyřešeno "+data.finished_date+")" : "";
			ids.txtArea.innerHTML = data.finished_code ? data.finished_code :  data.init;
			ids.txtBrief.innerHTML = data.title+solvStr;
			ids.txtDescr.innerHTML = data.zadani;
			ids.txtSolution.innerHTML = "Zde se objeví detaily testů tvého řešení";
			
			self.ta = ids.txtArea;
			self.b1 = ids.runtests;
			self.rs = ids.txtSolution;
			self.mainPnl = ids.pnlMain;
			self.btnHide = ids.btnHide
			self.nwBorder = ids.nwBorder;
			setup();
			return el;
		}
		
		this.setExecutable = function(ex) {
			this.b1.style.display = ex ? "": "none";
			self.ta.readOnly = !ex;
		}
	
		this.getElementOld = function() {
			var el = document.createElement("div"); // Hlavni prvek
			el.classList.add("txtTest");
			
			var solvStr = data.finished_date ? " (vyřešeno "+data.finished_date+")" : "";
			
			getNewElement2(el, "div", "txtTestLbl", data.title + solvStr);
			
			var wr = getNewElement1(el, "div", "txtWrap");
			self.mainPnl = wr;
			
			self.ta = getNewElement2(wr, "textarea", "txtAsm", data.init);
			
			var pc = getNewElement1(wr, "div", "pnlControls");
			self.b1 = getNewElement2(pc, "button", false, "Otestovat řešení");
				
	
			// Zadani
			var pz = getNewElement1(el, "div", "txtDescr");
			getNewElement2(pz, "div", "txtDescrName", "Popis");
			getNewElement2(pz, "span", "txtDescrTxt", data.zadani)
			
			// Reseni
			var sl = getNewElement1(el, "div", "txtDescr");
			getNewElement2(sl, "div", ["txtDescrName", "txtDescrNameSolution"], "Řešení");
			self.rs = getNewElement2(sl, "span", "txtDescrSolutionLog", "Zde se objevi detaily testů tvého řešení")
			setup();
			return el;
		};
		
		return this;
	}
	
	self.generateRandomString = function(length) {
		var result = "";
		var characters  = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		var charactersLength = characters.length;
		for (var i = 0; i < length; i++ ) {
			result += characters.charAt(Math.floor(Math.random() * charactersLength));
		}
		return result;
	}
	
	self.async = function(data, callbackOK, callbackFail) {
		var http = new XMLHttpRequest();
		var url = window.location.href+"test?cache="+self.generateRandomString(10);
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
	
	self.submit = function(id, asm, cbOK, cbFail) {
		var data = {"asm":asm, "id": id}
		var txtEnc = "q=" + self.encode(JSON.stringify(data));
		self.async(txtEnc, function(response) {
			var deco=self.decode(response);
			if(deco!==false) {
				var obj = JSON.parse(deco);
				if(obj!==false){
					if(obj.details) {
						console.log(JSON.stringify(obj.details));
					}
					if(obj.code == 0){
						cbOK(obj.result);
						return;
					} else {
						if(obj.code == 53) {
							cbFail(self.getErrorSolution(53));
						} else {
							cbFail(obj.result, obj.wait);
						}
						return;
					}
				}
				cbFail(self.getErrorSolution(1));
				return;
			}
			cbFail(self.getErrorSolution(0));
		}, cbFail);
	}
	
	self.getErrorSolution = function(code) {
		if(code == 0){
			return self.dec("<span class=\"log_err\">Nepodařilo se kontaktovat sestavovací server</span>");
		} else if(code == 1){
			return self.dec("<span class=\"log_err\">Nepodařilo se dekódovat odpověď sestavovacího serveru</span>");
		}else if(code == 53){
			return self.dec("<span class=\"log_err\">Byl jsi odhlášen. Pro přihlášení si obnov stránku (nezapomeň si někam bokem uložit kód, který se právě snažíš přeloži)</span>");
		} else {
			return self.dec("<span class=\"log_err\">Neznámá chyba</span>");
		}
	}
	
	self.setAllTestsEnabled = function(enabled) {
		for (var i = 0; i < allTests.length; i++) {
			self.allTests[i].setComponentsEnabled(enabled);
		}
	}
	
	self.setBlockTimeout = function(then) {
		if(window.timeoutIntervals){
			window.clearInterval(window.timeoutIntervals);
			window.timeoutIntervals = undefined;
		}
		window.timeoutIntervals = window.setInterval(function() {
			var els = document.getElementsByClassName("w1121");
			var now = new Date().getTime();
			var nText = "";
			if(then > now ){
				var fmt = function(d){
					var a = "00" + d;
					return a.substr(a.length-2,2);
				}
				
				var diff = then-now;
				diff/=1000;
				diff = Math.floor(diff);
				var sec = diff%60;
				diff = Math.floor((diff-sec)/60);
				var min = diff%60;
				diff = Math.floor((diff-min)/60);
				var hours = diff;
				var r = [];
				sec = fmt(sec);
				min = fmt(min);
				hours = fmt(hours);
				nText = hours+":"+min+":"+sec;
				
			} else {
				nText = "";
				window.clearInterval(window.timeoutIntervals);
				window.timeoutIntervals = undefined;
				self.setAllTestsEnabled(true);
			}
			for(var i = 0; i < els.length; i++) {
				var el = els[i];
				el.innerHTML = nText;
			}
		}, 1000);
	}
	
	self.runTest = function(tesx) {
		self.setAllTestsEnabled(false);
		var cbOK = function(data) {
			tesx.setSolution(data);
			tesx.setFinished(false);
			confetti.frameInterval = 15;
			confetti.maxCount = 900;
			confetti.start();
			setTimeout(function(){
				self.setAllTestsEnabled(true);
				confetti.stop();
			}, 5000);
		};
		
		var cbFail = function(descr, waiter) {
			descr = self.dec(descr);
			tesx.setSolution(descr);
			self.setAllTestsEnabled(true);
			
			var now = new Date().getTime();
			var then = waiter;
			if(then > now) {
				self.setAllTestsEnabled(false);
				self.setBlockTimeout(then);
			}
		}
		tesx.setSolution(self.dec("Vyhodnocuji test..."));
		self.submit(tesx.getID(), tesx.getASM(), cbOK, cbFail);
	}
	
	self.materialize = function(data, waiter) {
		window.waiter=waiter;
		id_indiv.innerHTML = "";
		allTests = [];
		for(var i = 0; i <data.length; i++) {
			data[i].title = self.dec(data[i].title);
			data[i].id = self.dec(data[i].id);
			data[i].zadani = self.dec(data[i].zadani);
			data[i].init = self.dec(data[i].init);
			var dataI = new self.testI(data[i], self);
			allTests[allTests.length] = dataI;
			id_indiv.appendChild(self.use_old_ui ? dataI.getElementOld() : dataI.getElement());
			if(data[i].finished_date){
				dataI.setFinished(true);
			}
			if(data[i].noexec) {
				dataI.setExecutable(false);
			}
			if(data[i].hidden) {
				if(data[i].hidden == 1) {
					dataI.setCollapsed(true);
				}
			}
		}
		var now = new Date().getTime();
		var then = waiter;
		if(then > now) {
			self.setAllTestsEnabled(false);
			self.setBlockTimeout(then);
		}
		if(!localStorage.getItem("rion.seen_faq")) {
			localStorage.setItem("rion.seen_faq",1);
			window.setTimeout(showFaq, 1000);
		}
	    window.setTimeout(self.mres, 1000);
	}
	
	self.loadRemoteTests = function() {
		var cbFail = function(data) {
			id_loader.innerHTML = self.dec(data);
			id_loader.classList.remove("loader");
			id_loader.classList.add("loader_error");
		};
		var cbOk = function(data) {
			var deco = self.decode(data);
			if(deco!==false){
				var jsn = JSON.parse(deco);
				if(jsn!==false){
					if(jsn.code === 0) {
						self.materialize(jsn.tests, jsn.wait);
						self.showLoginPanel();
					} else {
						if(jsn.code == 53){
							document.location.reload();
						} else {
							cbFail(self.dec("Nepodařilo se nahrát testy: <br />" + jsn.result));
						}
					}
					return;
				}
			}
			cbFail(self.dec("Nepodařilo se dekódovat testy"));
		};
		var data = {"action":"COLLECT"}
		var txtEnc = "q=" + self.encode(JSON.stringify(data));
		self.async(txtEnc, cbOk, cbFail);
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
			return self.dec(data);
		} else {
			return data;
		}
	}
	
	self.createGraph = function(parent, graphData) {
		graphData = self.sanitizeData(graphData);
		var ctx = document.createElement("canvas");
		var opts = graphData.LibOptions ? graphData.LibOptions : {};
		var ftype = graphData.Options.Type ? graphData.Options.Type : "line";
		if(graphData.Name){
			opts.title.text = graphData.Name;
		}
		if(graphData.Options && graphData.Options.Plag) {
			graphData.Data.x.unshift("Plagiátorů");
			graphData.Data.y[0].data.unshift(graphData.Options.Plag)
		}
		var chart = new Chart(ctx, {
		    type: ftype,
		    data: {
		        labels: graphData.Data.x,
		        datasets: graphData.Data.y
		    },
		    options: opts
		});
		parent.appendChild(ctx);
	}
	
	self.materializeGraphs = function(data) {
		var root = id_stats_contents;
		root.innerHTML = "";
		var rc = document.createElement("center");
		rc.style.display = "block";
		rc.style.position = "relative";
		rc.style.top = "80px";
		root.appendChild(rc);
		root = rc;
		
		id_stats_loader.style.display = "none";
		id_stats_contents.style.dipslay = "block";
		for(var dataI = 0; dataI < data.length; dataI++){
			var graphData = data[dataI];
			var child = document.createElement("div");
			child.style.border = "1px solid grey";
			child.style.padding = "30px";
			child.style.width = "50%";
			child.style.height = "20%";
			child.style.marginBottom = "50px";
			root.appendChild(child);
			self.createGraph(child, graphData);
		}
	}
	
	self.showFaq = function() {
		id_indiv.style.display = "none";
		id_faq.style.display = "block";
		txtHeader.style.display = "none";
		document.body.style.background = "black";
	}
	
	self.hideFaq = function() {
		id_indiv.style.display = "block";
		id_faq.style.display = "none";
		txtHeader.style.display = "block";
		document.body.style.background = "";
	}
	
	self.hideStats = function(){
		id_indiv.style.display = "block";
		id_stats.style.display = "none";
		txtHeader.style.display = "block";
		document.body.style.background = "";
	}
	
	self.showStats = function() {
		id_indiv.style.display = "none";
		id_stats_contents.innerHTML = "";
		id_stats.style.display = "block";
		id_stats_loader.style.display = "block";
		id_stats_contents.style.dipslay = "none";
		txtHeader.style.display = "none";
		document.body.style.background = "black";
		
		var cbFail = function(data) {
			self.hideStats();
		};
		var cbOk = function(data) {
			var deco = self.decode(data);
			if(deco!==false){
				var jsn = JSON.parse(deco);
				if(jsn!==false) { 
					if(jsn.code == 0) {
						self.materializeGraphs(jsn.data);
						return;
					}
				}
			}
			cbFail(self.dec("Nepodařilo se dekódovat statistiky"));
		};
		var data = {"action":"GRAPHS"}
		var txtEnc = "q=" + self.encode(JSON.stringify(data));
		self.async(txtEnc, cbOk, cbFail);
	}
	
	self.logout = function() {
	    var cookies = document.cookie.split(";");
	    for (var i = 0; i < cookies.length; i++) {
	        var cookie = cookies[i];
	        var eqPos = cookie.indexOf("=");
	        var name = eqPos > -1 ? cookie.substr(0, eqPos) : cookie;
	        document.cookie = name + "=;expires=Thu, 01 Jan 1970 00:00:00 GMT";
	    }
	    window.location.href="http://isu.rion.cz/logout"
	}
	
	self.aload = function() {
		window.addEventListener("resize", self.mres);
		txtLogin.innerHTML = self.IDENTITY_TOKEN.name + " ("+self.IDENTITY_TOKEN.primary+"@"+self.IDENTITY_TOKEN.group+")";
		btnLogout.addEventListener("click", function(){self.logout();});
		btnStats.addEventListener("click", function(){self.showStats();});
		btnFaq.addEventListener("click", function(){self.showFaq();});
		btnCloseStats.addEventListener("click", function() {self.hideStats();});
		btnCloseFaq.addEventListener("click", function() {self.hideFaq();});
		pnlWarnID.addEventListener("click", function() {self.showFaq();});
		self.loadRemoteTests();
		if(window.pastAload){
			window.pastAload();
		}
	}
	
	self.showLoginPanel =  function() {
		txtHeader.style.display="block";
	}
	
	self.use_old_ui  = false;
	
	self.reloadNewUI  = function() {
		use_old_ui = false;
		self.loadRemoteTests();
	}
	
	self.reloadOldUI = function() {
		use_old_ui = true;
		self.loadRemoteTests();
	}
}

function aload() {
	window.tester = new tester();
	tester.aload();
}

$INJECT_ADMIN$