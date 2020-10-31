var confetti={maxCount:150,speed:2,frameInterval:15,alpha:1,gradient:!1,start:null,stop:null,toggle:null,pause:null,resume:null,togglePause:null,remove:null,isPaused:null,isRunning:null};!function(){confetti.start=s,confetti.stop=w,confetti.toggle=function(){e?w():s()},confetti.pause=u,confetti.resume=m,confetti.togglePause=function(){i?m():u()},confetti.isPaused=function(){return i},confetti.remove=function(){stop(),i=!1,a=[]},confetti.isRunning=function(){return e};var t=window.requestAnimationFrame||window.webkitRequestAnimationFrame||window.mozRequestAnimationFrame||window.oRequestAnimationFrame||window.msRequestAnimationFrame,n=["rgba(30,144,255,","rgba(107,142,35,","rgba(255,215,0,","rgba(255,192,203,","rgba(106,90,205,","rgba(173,216,230,","rgba(238,130,238,","rgba(152,251,152,","rgba(70,130,180,","rgba(244,164,96,","rgba(210,105,30,","rgba(220,20,60,"],e=!1,i=!1,o=Date.now(),a=[],r=0,l=null;function d(t,e,i){return t.color=n[Math.random()*n.length|0]+(confetti.alpha+")"),t.color2=n[Math.random()*n.length|0]+(confetti.alpha+")"),t.x=Math.random()*e,t.y=Math.random()*i-i,t.diameter=10*Math.random()+5,t.tilt=10*Math.random()-10,t.tiltAngleIncrement=.07*Math.random()+.05,t.tiltAngle=Math.random()*Math.PI,t}function u(){i=!0}function m(){i=!1,c()}function c(){if(!i)if(0===a.length)l.clearRect(0,0,window.innerWidth,window.innerHeight),null;else{var n=Date.now(),u=n-o;(!t||u>confetti.frameInterval)&&(l.clearRect(0,0,window.innerWidth,window.innerHeight),function(){var t,n=window.innerWidth,i=window.innerHeight;r+=.01;for(var o=0;o<a.length;o++)t=a[o],!e&&t.y<-15?t.y=i+100:(t.tiltAngle+=t.tiltAngleIncrement,t.x+=Math.sin(r)-.5,t.y+=.5*(Math.cos(r)+t.diameter+confetti.speed),t.tilt=15*Math.sin(t.tiltAngle)),(t.x>n+20||t.x<-20||t.y>i)&&(e&&a.length<=confetti.maxCount?d(t,n,i):(a.splice(o,1),o--))}(),function(t){for(var n,e,i,o,r=0;r<a.length;r++){if(n=a[r],t.beginPath(),t.lineWidth=n.diameter,i=n.x+n.tilt,e=i+n.diameter/2,o=n.y+n.tilt+n.diameter/2,confetti.gradient){var l=t.createLinearGradient(e,n.y,i,o);l.addColorStop("0",n.color),l.addColorStop("1.0",n.color2),t.strokeStyle=l}else t.strokeStyle=n.color;t.moveTo(e,n.y),t.lineTo(i,o),t.stroke()}}(l),o=n-u%confetti.frameInterval),requestAnimationFrame(c)}}function s(t,n,o){var r=window.innerWidth,u=window.innerHeight;window.requestAnimationFrame=window.requestAnimationFrame||window.webkitRequestAnimationFrame||window.mozRequestAnimationFrame||window.oRequestAnimationFrame||window.msRequestAnimationFrame||function(t){return window.setTimeout(t,confetti.frameInterval)};var m=document.getElementById("confetti-canvas");null===m?((m=document.createElement("canvas")).setAttribute("id","confetti-canvas"),m.setAttribute("style","display:block;z-index:999999;pointer-events:none;position:fixed;top:0"),document.body.prepend(m),m.width=r,m.height=u,window.addEventListener("resize",function(){m.width=window.innerWidth,m.height=window.innerHeight},!0),l=m.getContext("2d")):null===l&&(l=m.getContext("2d"));var s=confetti.maxCount;if(n)if(o)if(n==o)s=a.length+o;else{if(n>o){var f=n;n=o,o=f}s=a.length+(Math.random()*(o-n)+n|0)}else s=a.length+n;else o&&(s=a.length+o);for(;a.length<s;)a.push(d({},r,u));e=!0,i=!1,c(),t&&window.setTimeout(w,t)}function w(){e=!1}}();

var tester = function() {
	
	var self = this;
	self.common = new Common();
	
	self.root = document.createElement("div");
	document.body.appendChild(self.root);
	
	self.allTests = [];
	
	self.testI = function(data, tester, common) {
		this.data = data;
		var self = this;
		var tester = tester;
		self.common = common;
		
		this.ta = null;
		this.b1 = null;
		this.rs = null;
		
		var UIOld = {
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
		
		var btnStyle = "height: 30px; padding-left: 20px; padding-right: 20px;";
		
		var UI = {
			"type": "table",
			"class": "tester_tbl",
			"style": "border-collapse: collapse; border-spacing: 0px; border: 1px solid black;width:80%;min-width: 840px;margin-bottom: 80px;font-family: Tahoma;left: 10%;position: relative;",
			"contents": [
				{
					"type": "thead",
					"contents": [
						{
							"type": "tr",
							"id": "nwBorder",
							"style": "background-color: #eeeeee;line-height: 50px;font-size: 20pt;font-weight: normal; border-bottom: 1px solid black",
							"contents": [
								{
									"type": "td",
									"innerHTML": "Header",
									"id": "txtBrief",
									"style": "width: 100%;padding-left:20px;"
								},
								{
									"type": "td",
									"style": "padding-right: 10px;width: 200px;text-align: right;",
									"contents": [
										{
											"type": "button",
											"innerHTML": "Skrýt",
											"id": "btnHide",
											"style": (btnStyle + "; position: relative; top: -5px; text-align: center; display: inline-block; width: 80px;padding: 0px;")
										}
									]		
								}
							]
						}
					]
				},
				{
					"type": "tbody",
					"contents": [
						{
							"type": "tr",
							"id": "pnlMain",
							"style": "border-bottom: 1px solid black",
							"contents": [
								{
									"type": "td",
									"colSpan": 2,
									"style": "margin: 0px; padding: 0px;",
									"contents": [
										{
											"type": "table",
											"style": "border-collapse: collapse; border: 0px; width: 100%; margin: 0px; padding: 0px;",
											"contents": [
												{
													"type": "tbody",
													"contents": [
														{
															"type": "tr",
															"contents": [
																{
																	"type": "td",
																	"contents": [
																		{
																			"type": "textarea",
																			"id": "txtArea",
																			"style": "width: calc(100% - 5px); min-height: 230px; padding-left: 5px; padding-right: 0px; outline: none; border-left: none; border-right: none; border-top: none; border-bottom: 1px solid black; background: #fafafa; font-family: courier; resize: vertical;",
																		}
																	]
																},
																{
																	"type": "td",
																	"rowSpan": 2,
																	"style": "vertical-align: top; width: 33%; border-left: 1px solid black; border-right: 1px solid black;margin: 0px; padding: 0px;",
																	"contents":[
																		{
																			"type": "div",
																			"style": "height: 40px; background-color: #daffff;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;border-bottom: 1px solid black",
																			"innerHTML": "Zadání"
																		},
																		{
																			"type": "div",
																			"style": "min-height: 230px;height: 100%;padding: 5px;font-family: times;",
																			"id": "txtDescr"
																		}
																	]	
																},
																{
																	"type": "td",
																	"rowSpan": 2,
																	"style": "width: 20%;min-width: 320px; vertical-align: top;",
																	"colSpan": 3,
																	"contents":[
																		{
																			"type": "div",
																			"style": "height: 40px; background-color: #ffddcc;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;border-bottom: 1px solid black",
																			"innerHTML": "Řešení"
																		},
																		{
																			"type": "div",
																			"style": "min-height: 230px;height: 100%;padding: 5px;font-family: times;",
																			"id": "txtSolution"
																		}
																	]
																}
															]
														},
														{
															"type": "tr",
															"contents": [
																{
																	"type": "td",
																	"style": "padding-right: 10px;width: 33%;",
																	"contents": [
																		{
																			"type": "button",
																			"innerHTML": "Otestovat řešení",
																			"id": "runtests",
																			"style": btnStyle + "; width: 80px;position: relative;margin-left: 5px;margin-bottom: 6px; width: 150px; height: 28px;"
																		},
																		{
																			"type": "button",
																			"innerHTML": "Historie řešení",
																			"id": "showhist",
																			"style": btnStyle + "; width: 80px;position: relative;margin-left: 5px;margin-bottom: 6px; width: 150px; height: 28px;"
																		}
																	]	
																}	
															]
														}
													]
												}
											]
										}
									]
								}
							]
						},
						{
							"type": "tr",
							"style": "",
							"contents": [
								{	
									"type": "td",
									"id": "feedbackPnl",
									"colSpan": 2,
									"style": "padding: 0px; margin: 0px; display: none"
								}	
							]
						}
					]
				}
			]
		}
		
		var feedbackListRowUI = {
			"type": "tr",
			"style": "border-top: 1px solid black",
			"contents": [
			     {
					"type": "td",
					"id": "turndate",
					"style": "padding-right: 10px; padding-top: 3px; padding-bottom: 3px;border-right: 1px solid black; text-align: right",
					"innerHTML": "01.01.1970 22:00"
				 },
				 {
					"type": "td",
					"id": "results",
					"style": "padding-right: 10px; padding-top: 3px; padding-bottom: 3px;border-right: 1px solid black; text-align: right",
					"innerHTML": "Chyba: abc"
				 },
			 	 {
					"type": "td",
					"id": "comments",
					"style": "padding-right: 10px; padding-top: 3px; padding-bottom: 3px;border-right: 1px solid black; text-align: right",
					"innerHTML": "0"
				 },
			 	 {
					"type": "td",
					"style": "padding-right: 10px; padding-top: 3px; padding-bottom: 3px;border-right: 1px solid black; text-align: left",
					"contents": [
						{
							"type": "span",
							"style": "margin-left: 10px",
							"id": "lastcommentlogin"
						},
						{
							"type": "span",
							"style": "float: right;margin-right: 5px;",
							"id": "lastcomment"
						}
					]
				 },
			 	 {
					"type": "td",
					"style": "padding-right: 5px; padding-top: 5px; padding-bottom: 5px;border-right: 1px solid black; text-align: right; min-width: 170px;",
					"contents": [
						{
							"type": "button",
							"id": "btnComment",
							"style": btnStyle,
							"innerHTML": "Zobrazit komentáře"
						}
					]
				 }
			]
		}
		
		var feedbackListUI = {
			"type": "table",
			"style": "border-collapse: collapse; border: 0px; width: 100%; margin: 0px; padding: 0px;",
			"contents": [
				{
					"type": "thead",
					"contents": [
						{
							"type":"tr",
							"style": "border-bottom: 1px solid black",
							"contents": [
								{
									"type": "td",
									"style": "border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 15%;",
									"innerHTML": "Datum odevzdání",
								},
								{
									"type": "td",
									"style": "border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 30%;",
									"innerHTML": "Výsledek testů",
								},
								{
									"type": "td",
									"style": "border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 15%;",
									"innerHTML": "Počet komentářů",
								},
								{
									"type": "td",
									"style": "border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 20%;",
									"innerHTML": "Poslední komentář",
								},
								{
									"type": "td",
									"style": "height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 150px;",
									"innerHTML": "Akce"
								}
							]
						}
					]
				},
				{
					"type": "tbody",
					"id": "feedback_contents"
				}
			]
		}
		
		var commentUI = {
			"type": "table",
			"style": "border-collapse: collapse; border-top: 0px; width: 100%; margin: 0px; padding: 0px;",
			"contents": [
				{
					"type": "tbody",
					"contents": [
						{
							"type":"tr",
							"contents": [
								{
									"type": "td",
									"style": "border-bottom: 1px solid black; border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 50%;",
									"innerHTML": "Odevzdaný kód",
								},
								{
									"type": "td",
									"rowSpan": 2,
									"style": "border-right: 1px solid black; height: 100%; margin: 0px; padding: 0px;width: calc(50% - 200px); vertical-align: top;",
									"contents": [
										{
											"type": "table",
											"style": "border-collapse: collapse; border: 0px; width: 100%; height: 100%; margin: 0px; padding: 0px;",
											"contents": [
												{
													"type": "thead",
													"contents": [
														{
															"type": "tr",
															"contents": [
																{
																	"type": "td",
																	"colSpan": 2,
																	"style": "border-bottom: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 200px;",
																	"innerHTML": "Komentáře"
																},
															]
														}
													]
												},
												{
													"type": "tbody",
													"id": "commentcontents"
												}
											]
										},
										{
											"type": "table",
											"style": "border-collapse: collapse; border: 0px; width: 100%; height: 40px; margin: 0px; padding: 0px;",
											"contents": [
												{
													"type": "thead",
													"contents": [
														{
															"type": "tr",
															"contents": [
																{
																	"type": "td",
																	"style": "border-bottom: 1px solid black; border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 200px;",
																	"innerHTML": "Označení"
																},
																{
																	"type": "td",
																	"style": "border-bottom: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: calc(100% - 200px);",
																	"innerHTML": "Komentář"
																}
															]
														}
													]
												},
												{
													"type": "tbody",
													"style": "border-collapse: collapse; border: 0px; width: 100%; height: 40px; margin: 0px; padding: 0px;",
													"contents": [
														{
															"type": "tr",
															"contents": [
																{
																	"type": "td",
																	"style": "border-right: 1px solid black; padding: 10px; vertical-align: top;",
																	"id": "commentSel"
																},
																{
																	"type": "td",
																	"contents": [
																		{
																			"type": "textarea",
																			"id": "commentArea",
																			"style": "min-height: 120px; position: relative; top: 5px; font-family: Verdana; font-size: 14pt; resize: vertical; height: calc(100% - 10px); width: calc(100% - 10px); outline: none; border: 0px;"
																		}
																	]																	
																}
															]
														}
													]
												}
											]
										}
									]
								},
								{
									"type": "td",
									"style": "border-bottom: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 200px;",
									"innerHTML": "Akce"
								}
							]
						},
						{
							"type": "tr",
							"contents": [
								{
									"type": "td",
									"style": "height: 100%;vertical-align: top;",
									"contents": [
										{
											"type": "textarea",
											"style": "min-height: 150px; padding: 5x; font-family: Courier; font-size: 12pt; border-right: 1px solid black;outline: none; width: calc(100% - 10px); height: calc(100% - 10px); resize: vertical;border: 0px",
											"id": "codecontents"
										}
									]
								},
								{
									"type": "td",
									"style": "border-left: 1px solid black; vertical-align: top; text-align: right;padding-right: 5px; padding-top: 5px; padding-bottom: 5px;",
									"contents": [
										{
											"type": "button",
											"id": "btnComment",
											"style": btnStyle,
											"innerHTML": "Přidat komentář"
										},
										{
											"type": "button",
											"id": "btnSave",
											"style": btnStyle + "; margin-top: 5px;display: none",
											"innerHTML": "Uložit komentář"
										},
										{
											"type": "button",
											"id": "btnAddSel",
											"style": btnStyle + "; margin-top: 5px;",
											"innerHTML": "Přidat označení"
										},
										{
											"type": "button",
											"id": "btnCancelEdit",
											"style": btnStyle + "; margin-top: 5px;display: none",
											"innerHTML": "Zrušit editaci"
										}
									]
								}
							]
						}
					]
				}
			]
		};
		
		this.setComponentsEnabled  = function(enabled) {
			self.ta.readOnly = !enabled;
			self.b1.disabled = !enabled;
			self.btnHist.disabled = !enabled;
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
				self.btnHist.addEventListener("click", self.showHistory);
			}
		}
		
		this.materializeFeedback = function(data) {
			if(data.sort) {
				data = data.sort(function(a, b) {
					if(a.CreationTime < b.CreationTime) {
						return 1;
					} else if(a.CreationTime > b.CreationTime) {
						return -1;
					} else {
						return 0;
					}
				});
			}
			var d = self.common.reconstructUI(feedbackListUI);
			var node = d[0];
			var ids = d[1];
			
			self.feedbackPnl.innerHTML = "";
			self.feedbackPnl.appendChild(node);
			
			
			var materializeRow = function(item) {
				var du = self.common.reconstructUI(feedbackListRowUI);
				var dnode = du[0];
				var dids = du[1];
				
				dids.results.innerHTML = item.Result;
				try {
					var jresult = JSON.parse(item.Result);
					if(jresult && jresult.result) {
						dids.results.innerHTML = jresult.result;
					}					 
				} catch (e) {
				}

				var commentsRow = document.createElement("tr");
				var commentsCell = document.createElement("td");
				commentsCell.colSpan = 5;
				commentsRow.style.borderTop = "1px solid black";
				commentsCell.innerHTML = "";
				commentsRow.appendChild(commentsCell);
				commentsRow.style.display = "none";
				
				dids.turndate.innerHTML = self.common.convertDateTime(item.CreationTime);
				dids.comments.innerHTML = item.TotalComments;
				dids.lastcomment.innerHTML = item.LastComment > 0 ? self.common.convertDateTime(item.LastComment) : "";
				dids.lastcommentlogin.innerHTML = item.LastCommentLogin !== undefined && item.LastCommentLogin !== "" ? item.LastCommentLogin : "";
				var openFN = function() {
					// load comments and show them
					var showLbl = "Skrýt komentáře";
					if(dids.btnComment.innerHTML == showLbl) {
						dids.btnComment.innerHTML = "Zobrazit komentáře";
						commentsCell.innerHTML = "";
						commentsRow.style.display = "none";
						dnode.style.borderLeft = "0px";
						dnode.style.borderRight = "0px";
						dnode.style.borderTop = "1px solid black";
						
						return;
					}
					
					var codeFmt = function(code) {
						var repl = [
							["&", "&amp;"],
							["<", "&lt;"],
							["\"", "&quot;"],
							[">", "&gt;"],
							["\t", "&nbsp;&nbsp;&nbsp;&nbsp;"],
							[" ", "&nbsp;"],
							["\n", "<br />"]
						]
						
						var splitter = function(item) {
							code = code.split(item[0]).join(item[1]);
						}
						repl.map(splitter);
						return code;
					}
					
					// Load comments UI
					self.common.showLoader();
					var cbFail = function(data) {
						self.common.hideLoader();
						self.common.showError("Chyba", "Nepodařilo se nahrát historii", true, data);
					};
					var reloadCB = function() {
						self.common.showError("Chyba", "Tohle by nemělo nikdy nastat");
					}
					
					var createCommentElements = function(root, selections, text, codeBlocks) {
						var findSel = function(id) {
							for(var i = 0; i < selections.length; i++) {
								if(selections[i][0] == id){
									return selections[i];
								}
							}
							return false;
						}
						
						var appendRaw = function(txt) {
							var span = document.createElement("span");
							span.innerHTML = codeFmt(txt);
							root.appendChild(span);
						}
						
						var appendSel = function(txt, begin, end) {
							var span = document.createElement("span");
							span.innerHTML = codeFmt(txt);
							span.style.cursor = "pointer";
							span.style.textDecoration = "underline";
							span.addEventListener("mouseover", function() {
								codeBlocks.setSelectionRange(begin, end);
								codeBlocks.focus();
							});
							span.addEventListener("click", function() {
								codeBlocks.setSelectionRange(begin, end);
								codeBlocks.focus();
							});
							root.appendChild(span);
						}
						
						
						var str = "";
						var code_0 = "0".charCodeAt(0);
						var code_9 = "9".charCodeAt(0);
						var code_delim = "@".charCodeAt(0);
						
						for(var i = 0; i < text.length; i++) {
							var c  = text.substr(i, 1);
							if(c == '\\' && i + 1 < text.length) { // Escape whatever
								i++;
								str +=  text.substr(i, 1);
							} else if(c != '@') {
								str += c;
							} else  {
								if(str != "") {
									appendRaw(str);
									str = "";
								}
								// find a number
								var num = 0;
								var foundEnd = false;
								i++;
								for(var o = i; o < text.length; o++, i++) {
									var code = text.substr(o, 1).charCodeAt(0);
									if(code >= code_0 && code <= code_9) {
										num *= 10;
										num += (code - code_0);
									} else if (code == code_delim) {
										foundEnd = true;
										break;
									} else { // Error
										root.innerHTML = "";
										appendRaw(text);
										return;
									}
								}
								if(!foundEnd) {
									root.innerHTML = "";
									appendRaw(text);
									return;
								}
								i++;
								var selection = findSel(num);
								if(selection === false) {
									root.innerHTML = "";
									appendRaw(text);
									return;
								}

								// Have selection, get the text
								foundEnd = false; 
								var txt = "";
								for(var o = i; o < text.length; o++, i++) {
									var c = text.substr(o, 1);
									if(c == "\\" && o + 1 < text.length) {
										i++;
										o++;
										txt += text.substr(o, 1);
									} else if(c == "@") {
										var toExpect = '@/' + num + '@';
										var following = text.substr(i, toExpect.length);
										if(toExpect == following) { // Valid sequence
											i += toExpect.length;
											foundEnd = true;
											break;
										} else {
											root.innerHTML = "";
											appendRaw(text);
										}
									} else {
										txt += c;
									}
								}					
								if(!foundEnd) {
									root.innerHTML = "";
									appendRaw(text);
								}				
								appendSel(txt, selection[1], selection[2]);
								i--;
							}
						}
						if(str != "") {
							appendRaw(str);
							str = "";
						}
					}
					
					var cbOk = function(data) {
						self.common.hideLoader();
						if(data && data.comments && data.comments.length !== undefined && data.data && data.data.Code){
							commentsRow.style.display = "";
							var xd = self.common.reconstructUI(commentUI);
							var xnode = xd[0];
							var xids = xd[1];
							
							xids.codecontents.readOnly=true;
							
							xids.commentcontents.innerHTML = "";
							
							var projectSelectionsToEditView = function(){
								self.common.showError("Chyba", "Tohle by se stát nemělo");
							}
							
							if(data.comments.sort) {
								data.comments = data.comments.sort(function(a, b) {
									if(a.Creation < b.Creation) {
										return 1;
									} else if(a.Creation > b.Creation) {
										return -1;
									} else {
										return 0;
									}
								});
							}
							
							data.comments.map(function(comment) {
								if(comment.AuthorLogin && comment.Data && comment.Data.selections && comment.Data.selections.length !== undefined && comment.Data.text !== undefined && comment.Creation) {
									var login = comment.AuthorLogin;
									var cdata = comment.Data
									
									var rowUI = {
										"type": "tr",
										"style": "border-bottom: 1px solid black",
										"contents": [
											{
												"type": "td",
												"colSpan": 2,
												"contents": [
													{
														"type": "div",
														"style": "width: 100%; display: block;background:#aaffdd;line-height: 30px;border-bottom: 1px solid black;",
														"contents": [
															{
																"type": "span",
																"style": "padding-left:10px;",
																"id": "idlogin"
															},
															{
																"type": "span",
																"style":"float:right;padding-right: 10px;",
																"contents": [
																	{
																		"type": "span",
																		"style": "display: inline-block; padding-right: 30px; ",
																		"id": "iddate"
																	},
																	{
																		"type": "button",
																		"id":"btnEdit",
																		"style": "padding-left: 10px; padding-right:10px;",
																		"innerHTML": "Editovat"
																	}
																]
															}
															
														]
													},
													{
														"type": "div",
														"style": "display: block; width: 100%; padding-bottom: 50px;padding-left: 5px; padding-top: 5px;",
														"id": "ctext"
													}
												]
											}
										]
									}
									
									var yd = self.common.reconstructUI(rowUI);
									var ynode = yd[0];
									var yids = yd[1];
									
									yids.idlogin.innerHTML = login;
									yids.iddate.innerHTML = self.common.convertDateTime(comment.Creation);
									if(!comment.Editable) {
										yids.btnEdit.style.display = "none";
									} else {
										yids.btnEdit.addEventListener("click", function(){ // Set to editable field
											projectSelectionsToEditView(comment.Data.selections, comment.Data.text, comment.ID);
										});
									}
									
									createCommentElements(yids.ctext, cdata.selections, cdata.text, xids.codecontents);
									
									xids.commentcontents.appendChild(ynode);
								}
							});
							
							var totalSelsCnt = 0;
							var selections = [];
			
							var addCurentSelection = function(start, end, selID) {
								var start = start === undefined ? xids.codecontents.selectionStart : start;
								var end = end === undefined ? xids.codecontents.selectionEnd : end;
								var len = end - start;
								if(len > 0) {
									var selID = selID === undefined ? totalSelsCnt : 0
									totalSelsCnt = selID + 1;
									var el = document.createElement("div");
									el.style.display = "block";
									el.style.width = "100%";
									
									var selData = [selID, start, end]
									selections.push(selData);
									
									var zn = len == 1 ? "znak" : len >= 2 && len <= 4 ? "znaky" : "znaků";
									
									
									var el2 = document.createElement("div");
									el2.style.display = "inline-block";
									el.appendChild(el2);
									
									var el3 = document.createElement("div");
									el3.style.display = "inline-block";
									el3.style.float = "right";
									el3.innerHTML = "[X]";
									el3.addEventListener("click", function() {
										// Delete this selection
										xids.commentSel.removeChild(el);
										
										selections = selections.filter(function(q) {return q[0] != selData[0]; });
									})
									
									el.appendChild(el3);
									
									el2.innerHTML = "[" + selID + "] (" + len + " " + zn + ")";
									el.style.cursor = "pointer";
									
									el2.addEventListener("mouseover", function(){
										xids.codecontents.setSelectionRange(start, end);
										xids.codecontents.focus();
									});
									el2.addEventListener("click", function(){
										xids.codecontents.setSelectionRange(start, end);
										xids.codecontents.focus();
										
										var editStart = xids.commentArea.selectionStart;
										var editEnd = xids.commentArea.selectionEnd;
										var originalPos = xids.commentArea.value.length - editEnd;  
										
										var pre = xids.commentArea.value.substr(0, editStart);
										var post = xids.commentArea.value.substr(editEnd);
										
										var str = xids.commentArea.value.substr(editStart, editEnd - editStart);
										
										var preTag = "@" + selID + "@";
										var postTag= "@/" + selID + "@";
										
										
										str = str == "" ? "popis označení" : str;
										
										str = pre + preTag + str + postTag + post;
										
										xids.commentArea.value = str;
										var newPos = str.length - originalPos;
										xids.commentArea.setSelectionRange(newPos, newPos);
										xids.commentArea.focus();
									});
									
									
									xids.commentSel.appendChild(el);
								}
							};
							
							var lastEditingCommendID = -1;
							
							var cancelProjectionToEditView = function() {
								selections = [];
								xids.commentSel.innerHTML = ""
								xids.btnSave.style.display = "none"
								xids.btnCancelEdit.style.display = "none"
								xids.btnComment.style.display = ""
								xids.commentArea.value = "";
								lastEditingCommendID = -1;
							}
							
							var projectSelectionsToEditView = function(sels, text, ID) {
								selections = [];
								xids.commentSel.innerHTML = ""
								xids.btnSave.style.display = ""
								xids.btnCancelEdit.style.display = ""
								xids.btnComment.style.display = "none"
								lastEditingCommendID = ID;
								
								sels.map(function(sel) {addCurentSelection(sel[1], sel[2], sel[0]); });
								xids.commentArea.value = text;
							}
											
							xids.btnCancelEdit.addEventListener("click", cancelProjectionToEditView);
							xids.btnSave.addEventListener("click", function(){
								var text = xids.commentArea.value.trim();
								var del = text == "";
								var query = {"selections": selections, "text": text};
								self.common.showLoader();
								var cbFailNewComment = function(err) {
									self.common.hideLoader();
									self.common.showError("Chyba", "Nepodařilo se uložit komentář", true, err);
								}
								var cbOkNewComment = function(data) {
									self.common.hideLoader();
									commentsCell.removeChild(xnode);
									reloadCB();
								}
								
								var data = {"action":"EDIT_FEEDBACK", "feedbackID": lastEditingCommendID, "feedbackData": query, "del": del};
								self.common.async(data, cbOkNewComment, cbFailNewComment, false);
							});
							
							// Create table for comments

							xids.codecontents.value = data.data.Code;
							xids.btnAddSel.addEventListener("click", function() {addCurentSelection()})
							xids.btnComment.addEventListener("click", function() {
								
								var text = xids.commentArea.value.trim();
								if(text.length == 0) {
									self.common.showError("Chyba", "Nelze uložit prázdný komentář", true);
									return;
								}
								
								// Save comment and reload
								var query = {"selections": selections, "text": text};
								self.common.showLoader();
								var cbFailNewComment = function(err) {
									self.common.hideLoader();
									self.common.showError("Chyba", "Nepodařilo se uložit komentář", true, err);
								}
								var cbOkNewComment = function(data) {
									self.common.hideLoader();
									commentsCell.removeChild(xnode);
									reloadCB();
								}
								
								var data = {"action":"STORE_FEEDBACK", "compilationID": item.ID, "feedbackData": query};
								self.common.async(data, cbOkNewComment, cbFailNewComment, false);
							});
							
							commentsCell.appendChild(xnode);							
						} else {
							cbFail("Neplatná příchozí struktura");
						}
					};
					var data = {"action":"COLLECT_FEEDBACK", "compilationID": item.ID};
					reloadCB = function() {
						self.common.async(data, cbOk, cbFail, false);
					}
					reloadCB();
					
		
					dids.btnComment.innerHTML = showLbl;
					commentsRow.style.display = "";
					dnode.style.borderLeft = "5px solid black";
					dnode.style.borderRight = "5px solid black";
					dnode.style.borderTop = "5px solid black";
					commentsRow.style.borderLeft = "5px solid black";
					commentsRow.style.borderRight = "5px solid black";
					commentsRow.style.borderBottom = "5px solid black";
				};
				
				dids.btnComment.addEventListener("click", openFN);
				
				ids.feedback_contents.appendChild(dnode);
				ids.feedback_contents.appendChild(commentsRow);
				
			}
			
			for(var i = 0; i < data.length; i++) {
				materializeRow(data[i]);
			}
		}
		
		this.showHistory = function() {
			var hideLbl = "Skrýt historii";
			if(self.btnHist.innerHTML == hideLbl) {
				self.feedbackPnl.style.display = "none";
				self.btnHist.innerHTML = "Historie řešení";
				return;
			}
			
			self.common.showLoader();
			var cbFail = function(data) {
				self.common.hideLoader();
				self.common.showError("Chyba", "Nepodařilo se nahrát historii", true, data);
			};
			var cbOk = function(data) {
				self.common.hideLoader();
				if(data.length === undefined) {
					cbFail("Neplatná příchozí struktura");
				} else {
					self.btnHist.innerHTML = hideLbl;
					self.feedbackPnl.style.display = "";
					self.materializeFeedback(data);					
				}
			};
			var data = {"action":"COLLECT_HISTORY", "testID": self.data.id+""};
			self.common.async(data, cbOk, cbFail, false);
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
			self.mainPnl.style.display = collapsed ?  "none" : "";
			if(self.marginPnl) { // True for new UI
				self.marginPnl.style.marginBottom = collapsed ? "10px": "80px";
			}
			if(self.btnHide){
				self.btnHide.innerHTML = collapsed ? "Zobrazit" : "Skrýt";
			}
			if(self.nwBorder && !self.marginPnl){
				self.nwBorder.style.borderLeft = collapsed ? "1px solid black" : "";
				self.nwBorder.style.borderRight = collapsed ? "1px solid black" : "";
			}
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
			self.btnHist = ids.showhist;
			self.nwBorder = ids.nwBorder;
			self.feedbackPnl = ids.feedbackPnl;
			self.marginPnl = el;
			setup();
			return el;
		}
		
		this.setExecutable = function(ex) {
			this.b1.style.display = ex ? "": "none";
			self.ta.readOnly = !ex;
		}
		return this;
	};
	
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
		self.root.innerHTML = "";
		self.allTests = [];
		for(var i = 0; i <data.length; i++) {
			data[i].title = data[i].title;
			data[i].id = data[i].id;
			data[i].zadani = data[i].zadani;
			data[i].init = data[i].init;
			var dataI = new self.testI(data[i], self, self.common);
			self.allTests[self.allTests.length] = dataI;
			var node = dataI.getElement();
			if(i == 0) {
				node.style.marginTop = "80px";
			}
			self.root.appendChild(node);
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
	}
	
	self.loadRemoteTests = function() {
		self.common.showInitLoader("Nahrávám testy...", "green");
		var cbFail = function(data) {
			self.common.showInitLoader("Nepodařilo se nahrát testy:<br />" + data);
		};
		var cbOk = function(data) {
			if(data && data.tests) {
				self.materialize(data.tests, data.wait);
				self.common.setLoginPanelVisible(true);
				self.common.hideInitLoader();
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
		var root = self.graphRootContent
		root.innerHTML = "";
		var rc = document.createElement("center");
		rc.style.display = "block";
		rc.style.position = "relative";
		rc.style.top = "80px";
		root.appendChild(rc);
		root = rc;
		
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
	
	var setBigPanel = function(root, closeCB) {
		root.style.display = "block";
		root.style.position = "fixed";
		root.style.left = "0px";
		root.style.right = "0px";
		root.style.top = "0px";
		root.style.bottom = "0px";
		root.style.background = "black";
		root.style.color = "white";
		
		var btn = document.createElement("button");
		btn.style.float = "right";
		btn.style.marginRight = "10px";
		btn.style.marginTop = "10px";
		btn.style.background = "black";
		btn.style.border = "1px solid white";
		btn.style.color = "white";
		btn.style.padding = "3px";
		btn.style.fontSize = "13pt";
		btn.style.paddingLeft = "50px";
		btn.style.paddingRight = "50px";
		btn.style.display = "block";
		btn.addEventListener("click", closeCB);
		btn.innerHTML = "Zavřít"
		
		var content = document.createElement("div");
		content.style.display ="block";
		content.style.position = "fixed"
		content.style.top = "40px";
		content.style.left = "0px";
		content.style.bottom = "0px";
		content.style.right= "0px";
	    content.style.overflowY = "auto";

		
		root.appendChild(btn);
		root.appendChild(content);
		
		return content;
	}
	

	self.showFaq = function() {
		self.common.setLoginPanelVisible(false);
		var d = document.getElementById("id_faq").innerHTML;
		self.faqRootContent.innerHTML = d;
		document.body.removeChild(self.root);
		document.body.appendChild(self.faqRoot);
	}
	
	self.hideFaq = function() {
		self.common.setLoginPanelVisible(true);
		document.body.removeChild(self.faqRoot);
		document.body.appendChild(self.root);
	}
	
	self.hideStats = function() {
		self.common.setLoginPanelVisible(true);
		document.body.removeChild(self.graphRoot);
		document.body.appendChild(self.root);
	}
	
	self.showStats = function() {
		self.common.setLoginPanelVisible(false);
		document.body.appendChild(self.graphRoot);
		document.body.removeChild(self.root);
		self.common.showInitLoader("Nahrávám statistiky", "green", "transparent", "white", self.graphRootContent);
		var cbFail = function(data) {
			self.common.hideInitLoader();
			self.hideStats();
		};
		var cbOk = function(data) {
			self.common.hideInitLoader();
			self.materializeGraphs(data);
		};
		var data = {"action":"GRAPHS"}
		self.common.async(data, cbOk, cbFail);
	}
	
	self.faqRoot = document.createElement("div");
	self.graphRoot = document.createElement("div");
	self.faqRootContent = setBigPanel(self.faqRoot, self.hideFaq);
	self.graphRootContent = setBigPanel(self.graphRoot, self.hideStats);
	

	
	self.aload = function() {
		self.common.addLoginPanel();
		self.common.setLoginPanelVisible(false);
		self.common.addButtonToLoginPanel("Statistiky", self.showStats);
		self.common.addButtonToLoginPanel("FAQ", self.showFaq);
		self.loadRemoteTests();
		
		if(window.pastAload){
			window.pastAload();
			//var admin = new Admin(self.common);
		}
	}
	
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