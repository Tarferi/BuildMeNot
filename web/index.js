var confetti={maxCount:150,speed:2,frameInterval:15,alpha:1,gradient:!1,start:null,stop:null,toggle:null,pause:null,resume:null,togglePause:null,remove:null,isPaused:null,isRunning:null};!function(){confetti.start=s,confetti.stop=w,confetti.toggle=function(){e?w():s()},confetti.pause=u,confetti.resume=m,confetti.togglePause=function(){i?m():u()},confetti.isPaused=function(){return i},confetti.remove=function(){stop(),i=!1,a=[]},confetti.isRunning=function(){return e};var t=window.requestAnimationFrame||window.webkitRequestAnimationFrame||window.mozRequestAnimationFrame||window.oRequestAnimationFrame||window.msRequestAnimationFrame,n=["rgba(30,144,255,","rgba(107,142,35,","rgba(255,215,0,","rgba(255,192,203,","rgba(106,90,205,","rgba(173,216,230,","rgba(238,130,238,","rgba(152,251,152,","rgba(70,130,180,","rgba(244,164,96,","rgba(210,105,30,","rgba(220,20,60,"],e=!1,i=!1,o=Date.now(),a=[],r=0,l=null;function d(t,e,i){return t.color=n[Math.random()*n.length|0]+(confetti.alpha+")"),t.color2=n[Math.random()*n.length|0]+(confetti.alpha+")"),t.x=Math.random()*e,t.y=Math.random()*i-i,t.diameter=10*Math.random()+5,t.tilt=10*Math.random()-10,t.tiltAngleIncrement=.07*Math.random()+.05,t.tiltAngle=Math.random()*Math.PI,t}function u(){i=!0}function m(){i=!1,c()}function c(){if(!i)if(0===a.length)l.clearRect(0,0,window.innerWidth,window.innerHeight),null;else{var n=Date.now(),u=n-o;(!t||u>confetti.frameInterval)&&(l.clearRect(0,0,window.innerWidth,window.innerHeight),function(){var t,n=window.innerWidth,i=window.innerHeight;r+=.01;for(var o=0;o<a.length;o++)t=a[o],!e&&t.y<-15?t.y=i+100:(t.tiltAngle+=t.tiltAngleIncrement,t.x+=Math.sin(r)-.5,t.y+=.5*(Math.cos(r)+t.diameter+confetti.speed),t.tilt=15*Math.sin(t.tiltAngle)),(t.x>n+20||t.x<-20||t.y>i)&&(e&&a.length<=confetti.maxCount?d(t,n,i):(a.splice(o,1),o--))}(),function(t){for(var n,e,i,o,r=0;r<a.length;r++){if(n=a[r],t.beginPath(),t.lineWidth=n.diameter,i=n.x+n.tilt,e=i+n.diameter/2,o=n.y+n.tilt+n.diameter/2,confetti.gradient){var l=t.createLinearGradient(e,n.y,i,o);l.addColorStop("0",n.color),l.addColorStop("1.0",n.color2),t.strokeStyle=l}else t.strokeStyle=n.color;t.moveTo(e,n.y),t.lineTo(i,o),t.stroke()}}(l),o=n-u%confetti.frameInterval),requestAnimationFrame(c)}}function s(t,n,o){var r=window.innerWidth,u=window.innerHeight;window.requestAnimationFrame=window.requestAnimationFrame||window.webkitRequestAnimationFrame||window.mozRequestAnimationFrame||window.oRequestAnimationFrame||window.msRequestAnimationFrame||function(t){return window.setTimeout(t,confetti.frameInterval)};var m=document.getElementById("confetti-canvas");null===m?((m=document.createElement("canvas")).setAttribute("id","confetti-canvas"),m.setAttribute("style","display:block;z-index:999999;pointer-events:none;position:fixed;top:0"),document.body.prepend(m),m.width=r,m.height=u,window.addEventListener("resize",function(){m.width=window.innerWidth,m.height=window.innerHeight},!0),l=m.getContext("2d")):null===l&&(l=m.getContext("2d"));var s=confetti.maxCount;if(n)if(o)if(n==o)s=a.length+o;else{if(n>o){var f=n;n=o,o=f}s=a.length+(Math.random()*(o-n)+n|0)}else s=a.length+n;else o&&(s=a.length+o);for(;a.length<s;)a.push(d({},r,u));e=!0,i=!1,c(),t&&window.setTimeout(w,t)}function w(){e=!1}}();

var tester = function() {
	
	var self = this;
	self.common = new Common();
	
	self.allTests = [];
	self.mres = function() {
		for(var i = 0; i < self.allTests.length; i++) {
			self.allTests[i].handleResize();
		}
	}
	
	
	self.testI = function(data, tester, common) {
		this.data = data;
		var self = this;
		var tester = tester;
		var common = common;
		
		this.ta = null;
		this.b1 = null;
		this.rs = null;
		
		var UI = {
		  "type": "div",
		  "class": "w0",
		  "contents": [
		    {
		      "type": "div",
		      "class": "w00",
		      "id": "nwBorder",
		      "contents": [
		        {
		          "type": "div",
		          "class": "w001",
		          "id": "txtBrief"
		        },
		        {
		          "type": "div",
		          "class": "w002",
		          "contents": [
		            {
		              "type": "Button",
		              "innerHTML": "Skrýt",
		              "id": "btnHide"
		            }
		          ]
		        }
		      ]
		    },
		    {
		      "type": "div",
		      "id": "pnlMain",
		      "class": "w1",
		      "contents": [
		        {
		          "type": "div",
		          "class": "w11",
		          "contents": [
		            {
		              "type": "textarea",
		              "class": "w111",
		              "id": "txtArea"
		            },
		            {
		              "type": "div",
		              "class": "w112",
		              "contents": [
		                {
		                  "type": "button",
		                  "id": "runtests",
		                  "innerHTML": "Otestovat řešení"
		                },
		                {
		                  "type": "div",
		                  "class": "w1121"
		                }
		              ]
		            }
		          ]
		        },
		        {
		          "type": "div",
		          "class": "w12",
		          "contents": [
		            {
		              "type": "div",
		              "class": "w121",
		              "innerHTML": "Zadání"
		            },
		            {
		              "type": "div",
		              "id": "txtDescr",
		              "class": "w122"
		            }
		          ]
		        },
		        {
		          "type": "div",
		          "class": "w13",
		          "contents": [
		            {
		              "type": "div",
		              "class": "w131",
		              "innerHTML": "Řešení"
		            },
		            {
		              "type": "div",
		              "id": "txtSolution",
		              "class": "w132"
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
		

		
		var setup = function() {
			// Mame vytvorenu strukturu a ulozeny jednotlive komponenty, nasadime callbacky
			self.b1.addEventListener("click", function() {tester.runTest(self);});
			
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
			var struct = tester.common.reconstructUI(UI);
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
	
		return this;
	}
	
	self.submit = function(id, asm, cbOK, cbFail) {
		var data = {"asm":asm, "id": id}
		self.common.async(data, function(response) {
			if(response!==false) {
				var obj = JSON.parse(response);
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
		}, cbFail, false);
	}
	
	self.getErrorSolution = function(code) {
		return self.common.getErrorSolution(code);
	}
	
	self.setAllTestsEnabled = function(enabled) {
		for (var i = 0; i < self.allTests.length; i++) {
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
			tesx.setSolution(descr);
			self.setAllTestsEnabled(true);
			
			var now = new Date().getTime();
			var then = waiter;
			if(then > now) {
				self.setAllTestsEnabled(false);
				self.setBlockTimeout(then);
			}
		}
		tesx.setSolution("Vyhodnocuji test...");
		self.submit(tesx.getID(), tesx.getASM(), cbOK, cbFail);
	}
	
	self.materialize = function(data, waiter) {
		window.waiter=waiter;
		id_indiv.innerHTML = "";
		self.allTests = [];
		for(var i = 0; i <data.length; i++) {
			data[i].title = data[i].title;
			data[i].id = data[i].id;
			data[i].zadani = data[i].zadani;
			data[i].init = data[i].init;
			var dataI = new self.testI(data[i], self, self.common);
			self.allTests[self.allTests.length] = dataI;
			id_indiv.appendChild(dataI.getElement());
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
		if(!localStorage.getItem("rion.seen_faq." + self.TOOLCHAIN)) {
			localStorage.setItem("rion.seen_faq." + self.TOOLCHAIN, 1);
			window.setTimeout(self.showFaq, 1000);
		}
	    window.setTimeout(self.mres, 1000);
	}
	
	self.loadRemoteTests = function() {
		var cbFail = function(data) {
			id_loader.innerHTML = data;
			id_loader.classList.remove("loader");
			id_loader.classList.add("loader_error");
		};
		var cbOk = function(data) {
			if(data && data.tests) {
				self.materialize(data.tests, data.wait);
				self.showLoginPanel();
			} else {
				cbFail("Nepodařilo se nahrát testy");
			}
		};
		var data = {"action":"COLLECT"}
		self.common.async(data, cbOk, cbFail);
	}

	
	self.createGraph = function(parent, graphData) {
		graphData = self.common.sanitizeData(graphData);
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
			self.materializeGraphs(data);
		};
		var data = {"action":"GRAPHS"}
		self.common.async(data, cbOk, cbFail);
	}

	
	self.aload = function() {
		self.loadRemoteTests();
		window.addEventListener("resize", self.mres);
		self.common.addButtonToLoginPanel = undefined
		
		txtLogin.innerHTML = self.common.identity.name + " ("+self.common.identity.primary+"@"+self.common.identity.group+")";
		btnLogout.addEventListener("click", function(){self.common.logout();});
		btnStats.addEventListener("click", function(){self.showStats();});
		btnFaq.addEventListener("click", function(){self.showFaq();});
		btnCloseStats.addEventListener("click", function() {self.hideStats();});
		btnCloseFaq.addEventListener("click", function() {self.hideFaq();});
		pnlWarnID.addEventListener("click", function() {self.showFaq();});
		if(window.Admin){
			admin = new Admin(self.common);
		}
	}
	
	self.showLoginPanel =  function() {
		txtHeader.style.display="block";
	}
	
	self.hideLoginPanel =  function() {
		txtHeader.style.display="none";
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
};


function aload() {
	window.tester = new tester();
	tester.aload();
}

$INJECT(common.js)$
$INJECT(WEB.ADMIN, admin.js)$