var confetti={maxCount:150,speed:2,frameInterval:15,alpha:1,gradient:!1,start:null,stop:null,toggle:null,pause:null,resume:null,togglePause:null,remove:null,isPaused:null,isRunning:null};!function(){confetti.start=s,confetti.stop=w,confetti.toggle=function(){e?w():s()},confetti.pause=u,confetti.resume=m,confetti.togglePause=function(){i?m():u()},confetti.isPaused=function(){return i},confetti.remove=function(){stop(),i=!1,a=[]},confetti.isRunning=function(){return e};var t=window.requestAnimationFrame||window.webkitRequestAnimationFrame||window.mozRequestAnimationFrame||window.oRequestAnimationFrame||window.msRequestAnimationFrame,n=["rgba(30,144,255,","rgba(107,142,35,","rgba(255,215,0,","rgba(255,192,203,","rgba(106,90,205,","rgba(173,216,230,","rgba(238,130,238,","rgba(152,251,152,","rgba(70,130,180,","rgba(244,164,96,","rgba(210,105,30,","rgba(220,20,60,"],e=!1,i=!1,o=Date.now(),a=[],r=0,l=null;function d(t,e,i){return t.color=n[Math.random()*n.length|0]+(confetti.alpha+")"),t.color2=n[Math.random()*n.length|0]+(confetti.alpha+")"),t.x=Math.random()*e,t.y=Math.random()*i-i,t.diameter=10*Math.random()+5,t.tilt=10*Math.random()-10,t.tiltAngleIncrement=.07*Math.random()+.05,t.tiltAngle=Math.random()*Math.PI,t}function u(){i=!0}function m(){i=!1,c()}function c(){if(!i)if(0===a.length)l.clearRect(0,0,window.innerWidth,window.innerHeight),null;else{var n=Date.now(),u=n-o;(!t||u>confetti.frameInterval)&&(l.clearRect(0,0,window.innerWidth,window.innerHeight),function(){var t,n=window.innerWidth,i=window.innerHeight;r+=.01;for(var o=0;o<a.length;o++)t=a[o],!e&&t.y<-15?t.y=i+100:(t.tiltAngle+=t.tiltAngleIncrement,t.x+=Math.sin(r)-.5,t.y+=.5*(Math.cos(r)+t.diameter+confetti.speed),t.tilt=15*Math.sin(t.tiltAngle)),(t.x>n+20||t.x<-20||t.y>i)&&(e&&a.length<=confetti.maxCount?d(t,n,i):(a.splice(o,1),o--))}(),function(t){for(var n,e,i,o,r=0;r<a.length;r++){if(n=a[r],t.beginPath(),t.lineWidth=n.diameter,i=n.x+n.tilt,e=i+n.diameter/2,o=n.y+n.tilt+n.diameter/2,confetti.gradient){var l=t.createLinearGradient(e,n.y,i,o);l.addColorStop("0",n.color),l.addColorStop("1.0",n.color2),t.strokeStyle=l}else t.strokeStyle=n.color;t.moveTo(e,n.y),t.lineTo(i,o),t.stroke()}}(l),o=n-u%confetti.frameInterval),requestAnimationFrame(c)}}function s(t,n,o){var r=window.innerWidth,u=window.innerHeight;window.requestAnimationFrame=window.requestAnimationFrame||window.webkitRequestAnimationFrame||window.mozRequestAnimationFrame||window.oRequestAnimationFrame||window.msRequestAnimationFrame||function(t){return window.setTimeout(t,confetti.frameInterval)};var m=document.getElementById("confetti-canvas");null===m?((m=document.createElement("canvas")).setAttribute("id","confetti-canvas"),m.setAttribute("style","display:block;z-index:999999;pointer-events:none;position:fixed;top:0"),document.body.prepend(m),m.width=r,m.height=u,window.addEventListener("resize",function(){m.width=window.innerWidth,m.height=window.innerHeight},!0),l=m.getContext("2d")):null===l&&(l=m.getContext("2d"));var s=confetti.maxCount;if(n)if(o)if(n==o)s=a.length+o;else{if(n>o){var f=n;n=o,o=f}s=a.length+(Math.random()*(o-n)+n|0)}else s=a.length+n;else o&&(s=a.length+o);for(;a.length<s;)a.push(d({},r,u));e=!0,i=!1,c(),t&&window.setTimeout(w,t)}function w(){e=!1}}();


if(!window.Tester) {
	window.Tester = {}
}

window.Tester.Main = function() {
	var self = this;
	
	var self = this;
	self.common = new Common();
	
	self.root = document.createElement("div");
	document.body.appendChild(self.root);
	
	self.allTests = [];

	self.setAllTestsEnabled = function(enabled) {
		for (var i = 0; i < self.allTests.length; i++) {
			self.allTests[i].setComponentsEnabled(enabled);
		}
	};
	
	self.materialize = function(data, waiter) {
		self.root.innerHTML = "";
		self.allTests = [];
		var forEveryOtherPanel = function(fn) {
			for(var i = 0; i < self.allTests.length; i++) {
				fn(self.allTests[i]);
			}
		}
		
		for(var i = 0; i < data.length; i++) {
			var pnl = new window.Tester.TestPanel(data[i], forEveryOtherPanel);
			self.allTests[self.allTests.length] = pnl;
			var node = pnl.getNode();
			if(i == 0) {
				node.style.marginTop = "80px";
			}
			self.root.appendChild(node);
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
		new Chart(ctx, {
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
			self.common.showError("Chyba", "Nepodařilo se nahrát statistiky", true, data);
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
	

	self.init = function() {
		self.common.addLoginPanel();
		self.common.setLoginPanelVisible(false);
		self.common.addButtonToLoginPanel("Statistiky", self.showStats);
		self.common.addButtonToLoginPanel("FAQ", self.showFaq);
		self.loadRemoteTests();
		
		if(window.pastAload){
			window.pastAload();
		}
	}
	
	self.init();
	return self;
};


function aload() {
	new window.Tester.Main();
}


window.Tester.FeedbackCommentPanel = function(comment, codeFmt, projectSelectionsToEditView, setSelectionRange) {
	var self = this;
	self.codeFmt = codeFmt;
	self.common = new Common();
	self.templates = new window.Tester.Templates();
	self.selections = [];
	
	self.init = function() {
		self.materialize();
	};
	
	self.getNode = function() {
		return self.node;
	}
	
	self.setSelectionRange = setSelectionRange;
	self.materialize = function() {

		var login = comment.AuthorLogin;
		var cdata = comment.Data

		var d = self.common.reconstructUI(self.templates.FeedbackLoadedRowUI);
		self.node = d[0];
		var ids = d[1];

		ids.idlogin.innerHTML = login;
		ids.iddate.innerHTML = self.common.convertDateTime(comment.Creation);
		if (!comment.Editable) {
			ids.btnEdit.style.display = "none";
		} else {
			ids.btnEdit.addEventListener("click", function() { // Set to editable field
				projectSelectionsToEditView(comment.Data.selections, comment.Data.text, comment.ID);
			});
		}

		self.selections = cdata.selections;
		self.createCommentElements(ids.ctext, cdata.text);
	};
	
	self.createCommentElements = function(root, text) {
	
		var findSel = function(id) {
			for (var i = 0; i < self.selections.length; i++) {
				if (self.selections[i][0] == id) {
					return self.selections[i];
				}
			}
			return false;
		};

		var appendRaw = function(txt) {
			var span = document.createElement("span");
			span.innerHTML = self.codeFmt(txt);
			root.appendChild(span);
		};

		var appendSel = function(txt, begin, end) {
			var span = document.createElement("span");
			span.innerHTML = self.codeFmt(txt);
			span.style.cursor = "pointer";
			span.style.textDecoration = "underline";
			span.addEventListener("mouseenter", function() {
				self.setSelectionRange(begin, end);
			});
			span.addEventListener("mouseleave", function() {
				self.setSelectionRange(0, 0);
			});
			root.appendChild(span);
		};


		var str = "";
		var code_0 = "0".charCodeAt(0);
		var code_9 = "9".charCodeAt(0);
		var code_delim = "@".charCodeAt(0);

		for (var i = 0; i < text.length; i++) {
			var c = text.substr(i, 1);
			if (c == '\\' && i + 1 < text.length) { // Escape whatever
				i++;
				str += text.substr(i, 1);
			} else if (c != '@') {
				str += c;
			} else {
				if (str != "") {
					appendRaw(str);
					str = "";
				}
				// find a number
				var num = 0;
				var foundEnd = false;
				i++;
				for (var o = i; o < text.length; o++, i++) {
					var code = text.substr(o, 1).charCodeAt(0);
					if (code >= code_0 && code <= code_9) {
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
				if (!foundEnd) {
					root.innerHTML = "";
					appendRaw(text);
					return;
				}
				i++;
				var selection = findSel(num);
				if (selection === false) {
					root.innerHTML = "";
					appendRaw(text);
					return;
				}

				// Have selection, get the text
				foundEnd = false;
				var txt = "";
				for (var o = i; o < text.length; o++, i++) {
					var c = text.substr(o, 1);
					if (c == "\\" && o + 1 < text.length) {
						i++;
						o++;
						txt += text.substr(o, 1);
					} else if (c == "@") {
						var toExpect = '@/' + num + '@';
						var following = text.substr(i, toExpect.length);
						if (toExpect == following) { // Valid sequence
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
				if (!foundEnd) {
					root.innerHTML = "";
					appendRaw(text);
				}
				appendSel(txt, selection[1], selection[2]);
				i--;
			}
		}
		if (str != "") {
			appendRaw(str);
			str = "";
		}
	};
	
	self.init();
	return this;
};

window.Tester.FeedbackCodePanel = function(node, codeFmt, originalCode, selectionNode, commentEditorNode) {
	var self = this;
	self.codeFmt = codeFmt;
	self.totalSelsCnt = 0;
	self.originalCode = originalCode;
	self.selections = [];

	self.setSelectionRange = function(begin, end) {
		node.innerHTML = "";
		var newCode = self.codeFmt(self.originalCode);
		if (end - begin > 0) {
			var pre = self.codeFmt(self.originalCode.substr(0, begin));
			var post = self.codeFmt(self.originalCode.substr(end));
			var sel = "<span style=\"background-color: #aaffee; border: 1px solid black;\">" + self.codeFmt(self.originalCode.substr(begin, end - begin)) + "</span>";
			newCode = pre + sel + post;
		}
		node.innerHTML = newCode;
	};

	self.getSelectionRange = function() {
		var sel = document.getSelection();
		if (sel.anchorNode && sel.anchorNode) {
			if (sel.anchorNode.parentNode == node && sel.rangeCount == 1) {
				var begin = sel.getRangeAt(0).startOffset;
				var end = sel.getRangeAt(0).endOffset;
				return [begin, end];
			}
		}
		return [0, 0];
	};

	self.addCurentSelection = function(start, end, selID) {
		var sels = self.getSelectionRange();
		var start = start === undefined ? sels[0] : start;
		var end = end === undefined ? sels[1] : end;
		var len = end - start;
		if (len > 0) {
			var selID = selID === undefined ? self.totalSelsCnt : selID
			self.totalSelsCnt = selID + 1;
			var el = document.createElement("div");
			el.style.display = "block";
			el.style.width = "100%";

			var selData = [selID, start, end]
			self.selections.push(selData);

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
				selectionNode.removeChild(el);
				self.selections = self.selections.filter(function(q) { return q[0] != selData[0]; });
			})

			el.appendChild(el3);

			el2.innerHTML = "[" + selID + "] (" + len + " " + zn + ")";
			el.style.cursor = "pointer";

			el2.addEventListener("mouseenter", function() {
				self.setSelectionRange(start, end);
			});
			el2.addEventListener("mouseleave", function() {
				self.setSelectionRange(0, 0);
			});

			el2.addEventListener("click", function() {
				self.setSelectionRange(start, end);

				var editStart = commentEditorNode.selectionStart;
				var editEnd = commentEditorNode.selectionEnd;
				var originalPos = commentEditorNode.value.length - editEnd;

				var pre = commentEditorNode.value.substr(0, editStart);
				var post = commentEditorNode.value.substr(editEnd);

				var str = commentEditorNode.value.substr(editStart, editEnd - editStart);

				var preTag = "@" + selID + "@";
				var postTag = "@/" + selID + "@";


				str = str == "" ? "popis označení" : str;

				str = pre + preTag + str + postTag + post;

				commentEditorNode.value = str;
				var newPos = str.length - originalPos;
				commentEditorNode.setSelectionRange(newPos, newPos);
				commentEditorNode.focus();
			});

			selectionNode.appendChild(el);
		}
	};

	self.init = function() {
		
	};


	self.init();
	return this;
};

window.Tester.LoadedFeedbackPanel = function(data, compilationID, codeFmt, reloadCB) {
	var self = this;
	self.data = data;
	self.compilationID = compilationID;
	self.codeFmt = codeFmt;
	self.common = new Common();
	self.templates = new window.Tester.Templates();
	self.lastEditingCommentID = -1;
	
	self.init = function() {
		self.materialize();
	};
	
	self.getNode = function() {
		return self.node;
	}
	
	self.cancelProjectionToEditView = function() {
		self.editor.selections = [];
		self.commentSel.innerHTML = ""
		self.btnSave.style.display = "none"
		self.btnCancelEdit.style.display = "none"
		self.btnComment.style.display = ""
		self.commentArea.value = "";
		self.lastEditingCommentID = -1;
	};

	self.projectSelectionsToEditView = function(sels, text, ID) {
		self.editor.selections = [];
		self.commentSel.innerHTML = ""
		self.btnSave.style.display = ""
		self.btnCancelEdit.style.display = ""
		self.btnComment.style.display = "none"
		self.lastEditingCommentID = ID;

		sels.map(function(sel) { self.editor.addCurentSelection(sel[1], sel[2], sel[0]); });
		self.commentArea.value = text;
	}
	
	
	self.materialize = function() {
		self.node = document.createElement("td");
		self.node .colSpan = 6;
		self.node.style.borderTop = "1px solid black";
		self.node .innerHTML = "";
		
		var d = self.common.reconstructUI(self.templates.commentUI);
		var node = d[0];
		var ids = d[1];
		
		self.node.appendChild(node);
		self.editor = new window.Tester.FeedbackCodePanel(ids.codecontents, self.codeFmt, data.data.Code, ids.commentSel, ids.commentArea);

		var originalCode = data.data.Code;
		self.btnSave = ids.btnSave;
		self.btnCancelEdit = ids.btnCancelEdit;
		self.commentSel = ids.commentSel;
		self.commentArea = ids.commentArea;
		self.btnComment = ids.btnComment;

		ids.codecontents.readOnly = true;
		ids.commentcontents.innerHTML = "";
		
		self.midcell = ids.midcell;
		self.leftCell = ids.leftCell;
		self.newComTable = ids.newComTable;
		self.comTable = ids.comTable;

		if (data.comments.sort) {
			data.comments = data.comments.sort(function(a, b) {
				if (a.Creation < b.Creation) {
					return 1;
				} else if (a.Creation > b.Creation) {
					return -1;
				} else {
					return 0;
				}
			});
		}

		data.comments.map(function(comment) {
			if (comment.AuthorLogin && comment.Data && comment.Data.selections && comment.Data.selections.length !== undefined && comment.Data.text !== undefined && comment.Creation) {
				var cmnt = new window.Tester.FeedbackCommentPanel(comment, self.codeFmt, self.projectSelectionsToEditView, self.editor.setSelectionRange);
				ids.commentcontents.appendChild(cmnt.getNode());
			}
		});

		self.lastEditingCommentID = -1;
		
		ids.btnCancelEdit.addEventListener("click", self.cancelProjectionToEditView);
		ids.btnSave.addEventListener("click", function() {
			var text = ids.commentArea.value.trim();
			var del = text == "";
			var query = { "selections": self.editor.selections, "text": text};
			self.common.showLoader();
			var cbFailNewComment = function(err) {
				self.common.hideLoader();
				self.common.showError("Chyba", "Nepodařilo se uložit komentář", true, err);
			}
			var cbOkNewComment = function(data) {
				self.common.hideLoader();
				self.node.parentNode.removeChild(self.node);
				reloadCB();
			}

			var data = { "action":"EDIT_FEEDBACK", "feedbackID": self.lastEditingCommentID, "feedbackData": query, "del": del};
			self.common.async(data, cbOkNewComment, cbFailNewComment, false);
		});

		// Create table for comments

		ids.codecontents.innerHTML = self.codeFmt(data.data.Code);
		ids.btnAddSel.addEventListener("click", function() { self.editor.addCurentSelection(); })
		ids.btnComment.addEventListener("click", function() {

			var text = ids.commentArea.value.trim();
			if (text.length == 0) {
				self.common.showError("Chyba", "Nelze uložit prázdný komentář", true);
				return;
			}

			// Save comment and reload
			var query = { "selections": self.editor.selections, "text": text };
			self.common.showLoader();
			var cbFailNewComment = function(err) {
				self.common.hideLoader();
				self.common.showError("Chyba", "Nepodařilo se uložit komentář", true, err);
			}
			var cbOkNewComment = function(data) {
				self.common.hideLoader();
				self.node.parentNode.removeChild(self.node);
				reloadCB();
			}

			var data = { "action": "STORE_FEEDBACK", "compilationID": self.compilationID, "feedbackData": query};
			self.common.async(data, cbOkNewComment, cbFailNewComment, false);
		});

	};
	
	self.handleInitSize = function() {
		self.midcell.style.height = self.leftCell.offsetHeight + "px";
		self.newComTable.style.height = (self.midcell.offsetHeight - self.comTable.offsetHeight) + "px";
	}
	
	self.init();
	return this;
};

window.Tester.FeedbackPanel = function(data, showLoginColumn) {
	var self = this;
	self.data = data;
	self.common = new Common();
	self.templates = new window.Tester.Templates();
	self.showLbl = "Skrýt komentáře";
	
	self.init = function() {
		self.detailRoot = document.createElement("tr");
		self.materialize(self.data);
	}

	self.codeFmt = function(code) {
		var repl = [
			["&", "&amp;"],
			["<", "&lt;"],
			[">", "&gt;"]
		]

		var splitter = function(item) {
			code = code.split(item[0]).join(item[1]);
		}
		repl.map(splitter);
		return code;
	}
	
	self.getNodes = function() {
		return [self.node, self.detailRoot];
	}
	
	self.isVisible = function() {
		return self.btnComment.innerHTML == self.showLbl;
	};
	
	self.setVisible = function(vis) {
		if (!vis) {
			if(self.loadedPnl !== undefined) {
				var node = self.loadedPnl.getNode();
				if(node.parentNode == self.detailRoot){
					self.detailRoot.removeChild(node);
				}
				self.loadedPnl = undefined;
			}
			
			self.btnComment.innerHTML = "Zobrazit komentáře";
			self.detailRoot.style.display = "none";
			self.node.style.borderLeft = "0px";
			self.node.style.borderRight = "0px";
			self.node.style.borderTop = "1px solid black";
			return;
		}
		self.btnComment.innerHTML = self.showLbl;
		self.load();
	};
	
	self.reload = function() {
		self.load();
	}
	
	self.load = function() {

		// Load comments UI
		self.common.showLoader();
		var cbFail = function(data) {
			self.common.hideLoader();
			self.common.showError("Chyba", "Nepodařilo se nahrát historii", true, data);
		};

		var cbOk = function(data) {
			self.common.hideLoader();
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
			self.totalComments.innerHTML = data.comments.length;
			if(data.comments.length > 0) {
				self.lastcomment.innerHTML = data.comments[0].Creation > 0 ? self.common.convertDateTime(data.comments[0].Creation) : "";
				self.lastcommentlogin.innerHTML = data.comments[0].LastCommentLogin !== undefined && data.comments[0].LastCommentLogin !== "" ? data.comments[0].LastCommentLogin : "";
			} else {
				self.lastcomment.innerHTML = "";
				self.lastcommentlogin.innerHTML = "";
			}
			
			if (data && data.comments && data.comments.length !== undefined && data.data && data.data.Code) {
				self.loadedPnl = new window.Tester.LoadedFeedbackPanel(data, self.data.ID, self.codeFmt, self.reload);
				self.detailRoot.appendChild(self.loadedPnl.getNode());
				self.loadedPnl.handleInitSize();
			} else {
				cbFail("Neplatná příchozí struktura");
			}
		};
		var data = { "action":"COLLECT_FEEDBACK", "compilationID": self.data.ID};
		self.common.async(data, cbOk, cbFail, false);

		self.btnComment.innerHTML = self.showLbl;
		self.detailRoot.style.display = "";
		self.node.style.borderLeft = "5px solid black";
		self.node.style.borderRight = "5px solid black";
		self.node.style.borderTop = "5px solid black";
		self.detailRoot.style.borderLeft = "5px solid black";
		self.detailRoot.style.borderRight = "5px solid black";
		self.detailRoot.style.borderBottom = "5px solid black";
		
	}

	self.materialize = function(item) {
		var d = self.common.reconstructUI(self.templates.feedbackListRowUI);
		var node = d[0];
		var ids = d[1];
		
		self.node = node;
		self.btnComment = ids.btnComment;

		ids.results.innerHTML = item.Result;
		try {
			var jresult = JSON.parse(item.Result);
			if (jresult && jresult.result) {
				ids.results.innerHTML = jresult.result;
			}
		} catch (e) {
		}
		
		self.lastcomment = ids.lastcomment;
		self.lastcommentlogin = ids.lastcommentlogin;
		self.totalComments = ids.comments;

		ids.turndate.innerHTML = self.common.convertDateTime(item.CreationTime);
		ids.comments.innerHTML = item.TotalComments;
		ids.loginCell.style.display = showLoginColumn ? "" : "none";
		ids.loginCell.innerHTML = item.Login;

		ids.lastcomment.innerHTML = item.LastComment > 0 ? self.common.convertDateTime(item.LastComment) : "";
		ids.lastcommentlogin.innerHTML = item.LastCommentLogin !== undefined && item.LastCommentLogin !== "" ? item.LastCommentLogin : "";
		
		var openFN = function() {
			self.setVisible(!self.isVisible());
		};

		ids.btnComment.addEventListener("click", openFN);

	}

	self.init();
	return this;
};


window.Tester.HistoryPanel = function(data, historyBtn, rowPnl) {
	var self = this;
	self.data = data;
	var hideLbl = "Skrýt historii";
	
	self.isCollapsed = false;
	
	self.common = new Common();
	self.templates = new window.Tester.Templates();
	
	self.setComponentsEnabled  = function(enabled) {
		historyBtn.disabled = !enabled;
		self.setVisible(false);
	};
	
	self.init = function() {
		historyBtn.addEventListener("click", self.btnClick);
		
		var d = self.common.reconstructUI(self.templates.feedbackListUI);
		var node = d[0];
		var ids = d[1];
		
		rowPnl.innerHTML = "";
		rowPnl.appendChild(node);
		self.loginHeader = ids.loginHeader;
		self.node = node;
		self.nodeContents = ids.feedback_contents;
		
	};
	
	self.isVisible = function() {
		return historyBtn.innerHTML == hideLbl;
	}
	
	self.setCollapsed = function(collapsed) {
		if(self.isCollapsed && !collapsed && !self.isVisible()) { // was collapsed and shouldn't be no more
			self.isCollapsed = false;
			self.setVisible(true);
		} else if(!self.isCollapsed && collapsed && self.isVisible()) {
			self.isCollapsed = true;
			self.setVisible(false);
		}
	}
	
	self.setVisible = function(vis) {
		if(!vis) {
			rowPnl.style.display = "none";
			historyBtn.innerHTML = "Historie řešení";
			return;
		}
		self.loadData();
	}
	
	self.btnClick = function() {
		self.setVisible(!self.isVisible());		
	};
	
	self.loadData = function() {
		self.common.showLoader();
		self.nodeContents.innerHTML = "";
		var cbFail = function(data) {
			rowPnl.style.display = "none";
			self.common.hideLoader();
			self.common.showError("Chyba", "Nepodařilo se nahrát historii", true, data);
		};
		var cbOk = function(data) {
			self.common.hideLoader();
			if(data.length === undefined) {
				cbFail("Neplatná příchozí struktura");
			} else {
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
				var showLoginColumn = data.reduce(function(total, item){return total || item.Login != self.common.identity.login;},false);
				self.loginHeader.style.display = showLoginColumn ? "" : "none";
				historyBtn.innerHTML = hideLbl;
				rowPnl.style.display = "";
				var allFeedbacks = [];
				data.map(function(item){
					var pnl = new window.Tester.FeedbackPanel(item, showLoginColumn);
					allFeedbacks.push(pnl)
					pnl.getNodes().map(function(node) {
						self.nodeContents.appendChild(node);
					});
				});
			}
		};
		var data = {"action":"COLLECT_HISTORY", "testID": self.data.id + ""};
		self.common.async(data, cbOk, cbFail, false);
	}
	
	self.init();
	return this;
};


window.Tester.TestPanel = function(data, forEveryOtherPanelCB) {
	var self = this;

	this.data = data;
	self.forEveryOtherPanelCB = forEveryOtherPanelCB;
	self.common = new Common();
	self.templates = new window.Tester.Templates();
	
	self.codeArea = null;

	self.node = null;
	self.btnRun = null;
	self.resultArea = null;


	self.setBlockTimeout = function(then) {
		if (window.timeoutIntervals) {
			window.clearInterval(window.timeoutIntervals);
			window.timeoutIntervals = undefined;
		}
		window.timeoutIntervals = window.setInterval(function() {
			var now = new Date().getTime();
			var nText = "";
			var enabled = false;
			if(then > now ) {
				var diff = then-now;
				nText = self.common.convertTime(diff);
			} else {
				nText = "";
				window.clearInterval(window.timeoutIntervals);
				window.timeoutIntervals = undefined;
				enabled = true;
			}
			self.setComponentsEnabled(enabled);
			self.forEveryOtherPanelCB(function(anotherSelf) {
				anotherSelf.timeoutLbl.innerHTML = nText;
			});
		}, 1000);
	};
	
	self.runTest = function() {
		self.setComponentsEnabled(false);
		var cbFail = function(descr, waiter) {
			self.setResult(descr);
			self.setComponentsEnabled(true);
			
			var now = new Date().getTime();
			var then = waiter;
			if(then > now) {
				self.setComponentsEnabled(false);
				self.setBlockTimeout(then);
			}
		}
		var cbOK = function(data) {
			if(data) {
				data = JSON.parse(data);
				if(data !== undefined && data.code !== undefined && data.result !== undefined) {
					if(data.code == 53) {
						cbFail("Uživatel není přihlášen");
						return;
					} else if(data.code == 0) {
						self.setResult(data.result);
						self.setFinished(false);
						confetti.frameInterval = 15;
						confetti.maxCount = 900;
						confetti.start();
						setTimeout(function(){self.setComponentsEnabled(true);confetti.stop();}, 5000);
					} else {
						cbFail(data.result, data.waiter);
					}
				}
			}
		};
		self.setResult("Vyhodnocuji test...");
		self.common.async( {"action":"RUN_TEST", "asm": self.getASM(), "id": self.data.id}, cbOK, cbFail, false);
	}
	
	this.setComponentsEnabled  = function(enabled) {
		forEveryOtherPanelCB(function(anotherSelf) {
			anotherSelf.codeArea.readOnly = !enabled;
			anotherSelf.btnRun.disabled = !enabled;
			anotherSelf.btnHist.disabled = !enabled;
			if(anotherSelf.historyPnl) {
				anotherSelf.historyPnl.setComponentsEnabled(enabled);
			}
		});
	}
	
	this.setResult = function(resultText) {
		self.resultArea.innerHTML = resultText;
	}
	
	self.getASM = function() {
		return self.codeArea.value;
	}
	
	self.setCode = function(data) {
		self.codeArea.value = data;
	}
	
	self.init = function() {

		// Materialize
		var d = self.common.reconstructUI(self.templates.UI);
		var node = d[0];
		var ids = d[1];
		var solvStr = data.finished_date ? " (vyřešeno "+data.finished_date+")" : "";
		
		ids.txtArea.innerHTML = data.finished_code ? data.finished_code :  data.init;
		ids.txtBrief.innerHTML = data.title+solvStr;
		ids.txtDescr.innerHTML = data.zadani;
		
		self.codeArea = ids.txtArea;
		self.btnRun = ids.runtests;
		self.resultArea = ids.txtSolution;
		self.mainPnl = ids.pnlMain;
		self.btnHide = ids.btnHide
		self.btnHist = ids.showhist;
		self.nwBorder = ids.nwBorder;
		self.timeoutLbl = ids.timeoutLbl;
		self.node = node;


		// Tab handler for text area
		var cancF = function(event) {
			if(event.keyCode === 9) {
				var v = self.value
				var s = self.selectionStart
				var e = self.selectionEnd;
				self.value = v.substring(0, s)+'\t'+v.substring(e);
				self.selectionStart = self.selectionEnd = s + 1;
				event.preventDefault();
				return false;
			}
		}
		
		var clickCB = function() {
			if(self.isCollapsed()) {
				self.setCollapsed(false);
			} else {
				self.setCollapsed(true);
			}
		};
		
		self.btnHide.addEventListener("click", clickCB);
		self.codeArea.addEventListener("keydown", cancF);
		self.btnRun.addEventListener("click", function() {self.runTest();});
		
		self.historyPnl = new window.Tester.HistoryPanel(self.data, self.btnHist, ids.feedbackPnl);
		
		if(data.finished_date) {
			self.setFinished(true);
		}
		if(data.noexec) {
			self.setExecutable(false);
		}
		if(data.hidden === 1) {
			self.setCollapsed(true);
		}
	}
	
	self.getNode = function() {
		return self.node;
	}
	
	self.showHistory = function() {
		self.showHistory.btnClick();
	}
	
	self.setFinished = function(collapse) {
		if(collapse) {
			self.setCollapsed(true);
		}
		if(self.nwBorder){
			self.nwBorder.style.background = "#68CD34";
		}
	}
	
	self.isCollapsed = function() {
		return self.mainPnl.style.display == "none";
	}
	
	self.setCollapsed = function(collapsed) {
		self.historyPnl.setCollapsed(collapsed);
		
		self.mainPnl.style.display = collapsed ?  "none" : "";
		if(self.marginPnl) { // True for new UI
			self.marginPnl.style.marginBottom = collapsed ? "10px": "80px";
		}
		if(self.btnHide){
			self.btnHide.innerHTML = collapsed ? "Zobrazit" : "Skrýt";
		}
		if(self.nwBorder && !self.marginPnl) {
			self.nwBorder.style.borderLeft = collapsed ? "1px solid black" : "";
			self.nwBorder.style.borderRight = collapsed ? "1px solid black" : "";
		}
	}
	
	self.setExecutable = function(ex) {
		self.btnRun.style.display = ex ? "": "none";
		self.codeArea.readOnly = !ex;
	}
	
	self.init();
	return this;
};

$INJECT(tester/templates.js)$
$INJECT(common.js)$
$INJECT(WEB.ADMIN, admin.js)$