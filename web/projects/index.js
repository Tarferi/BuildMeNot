window.inject("common.js");

function ProjecterNoAccess() {
	self.common.showError("Chyba", "Nemáte dostatečná práva k přístupu k tomuto dokumentu", false);
}

window.inject_code_noperms("WEB.PROJECTS", "window.aload = ProjecterNoAccess;");


if(!window.Projecter) {
	window.Projecter = {}
}


window.Projecter.ProjectSettings = function(data, reloadCB, noAdminCB) {
	var self = this;
	self.common = new Common();
	self.templates = new window.Projecter.Templates();
	self.root = null;
	
	var save = function(newTitle, newDescription, newFileList, newEditorList, newCommentList, newVisibleFilesList, newPrefix) {
		newTitle = newTitle.trim();
		newDescription = newDescription.trim();
		newPrefix = newPrefix.trim();
		
		if(newTitle == "") {
			self.common.showError("Chyba", "Název projektu nesmí být prázdný", true);
			return;
		}
		if(newDescription == "") {
			self.common.showError("Chyba", "Popis projektu nesmí být prázdný", true);
			return;
		}
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showError("Chyba ukládání dat", "Nepodařilo se uložit změny", true, err);
		}
		var cbOK = function(data) {
			self.common.hideLoader();
			if(data !== true) {
				return cbFail(data);
			}
			reloadCB();
		}
		self.common.showLoader();
		var asyncData = {
			"action" : "HANDLE_PROJECTS",
			"project_data" : "saveProject",
			"project_id": data.ID,
			"title": newTitle,
			"description": newDescription,
			"files": newFileList, 
			"editor_files" : newEditorList,
			"comment_files": newCommentList,
			"visible_files": newVisibleFilesList,
			"prefix": newPrefix
		};
		self.common.async(asyncData, cbOK, cbFail, true);	
	}
	
	var reduceFileList = function(fileList) {
		var result = [];
		for(var fileName in fileList) {
			if(fileList.hasOwnProperty(fileName)){
				var fileData = fileList[fileName];
				result.push(fileName+" = " + fileData);
			}
		}
		return result.join("\n");
	}
	
	var unreduceFileList = function(str) {
		var res = {};
		str.split("\n").map(function(line){
			if(line.trim().length > 0) {
				if(line.indexOf("=") >= 0) {
					var name = line.substr(0, line.indexOf("=")).trim();
					var mname = line.substr(line.indexOf("=")+1).trim();
					res[name] = mname;
				}
			}
		});
		return res;
	}
	
	var notEmpty = function(line) {
		return line.trim().length > 0;
	}
	var trimmed = function(line) {
		return line.trim();
	}
	
	self.init = function() {
		var d = self.common.reconstructUI(self.templates.cardSettings);
		var el = d[0];
		var ids = d[1];
		
		var admin = data.config.visible_files !== undefined;
		if(admin) {
			ids.txtName.value = data.config.title;
			ids.txtDescr.value = data.config.description;
			ids.txtFiles.value = reduceFileList(data.config.files);
			ids.txtEditors.value = reduceFileList(data.config.editor_files);
			ids.txtCommentFiles.value = data.config.comment_files.join("\n");
			ids.txtVisibleFiles.value = data.config.visible_files.join("\n");
			ids.txtPrefix.value = data.config.prefix;
			ids.btnSave.addEventListener("click", function(){
				var newName = ids.txtName.value.trim();
				var newDescription = ids.txtDescr.value.trim();
				var newFiles = unreduceFileList(ids.txtFiles.value);
				var newEditors = unreduceFileList(ids.txtEditors.value);
				var newCommentFiles = ids.txtCommentFiles.value.split("\n").map(trimmed).filter(notEmpty);
				var newVisibleFiles =  ids.txtVisibleFiles.value.split("\n").map(trimmed).filter(notEmpty);
				var newPrefix =  ids.txtPrefix.value.trim();
				save(newName, newDescription, newFiles, newEditors, newCommentFiles, newVisibleFiles, newPrefix);
			});
		} else {
			noAdminCB();
		}
		
		self.root = el;
	}
	
	self.getElement = function() {
		return self.root;
	}
	
	self.newlyOpened = function() {
		
	}
	
	self.init();
	
	return this;
}

window.Projecter.Tree = function() {
	var self = this;
	self.common = new Common();
	
	var nodes = [];
	
	var root = document.createElement("ul");

	var projectNodes = function() {
		root.innerHTML = "";
		root.style.listStyleType = "none";
		
		self.rec_node = function(parent, node) {
			var contents = node.contents;
			var text = node.text;
			
			var el = document.createElement("li");
			
			el.style.diplay = "block";
			el.style.marginTop = "5px";
			el.style.marginBottom = "5px";
			el.style.borderTop = "1px solid black";
			
			if(node.click) {
				el.style.cursor = "pointer";
				el.addEventListener("click", function(){node.click(node);});
			}
			
			if(contents && contents.length !== undefined) {
				var elCaret = document.createElement("span");
				var elCaretBefore = document.createElement("span");
				var elCaretText = document.createElement("span");
				
				elCaret.style.cursor = "pointer";
				elCaret.style.userSelect = "none";
				elCaret.style.borderLeft = "1px solid black";
				
				elCaretText.innerHTML = text;
				
				elCaretBefore.innerHTML = "\u25B6";
				elCaretBefore.style.color = "black";
				elCaretBefore.style.display = "inline-block";
				elCaretBefore.style.marginRight = "6px";
				
				
				elCaret.appendChild(elCaretBefore);
				elCaret.appendChild(elCaretText);
				el.appendChild(elCaret);
				
				var subEl = document.createElement("ul");
				subEl.style.listStyleType = "none";
				subEl.style.margin = "0px;"
				subEl.style.padding = "0px";
				subEl.style.marginLeft = "40px";
				subEl.style.display = "none";
				
				el.appendChild(subEl);
				
				elCaretBefore.addEventListener("click", function(){
					var isExpanded = subEl.style.display != "none";
					if(isExpanded) {
						elCaretBefore.style.transform = ""
						subEl.style.display = "none";
					} else {
						elCaretBefore.style.transform = "rotate(90deg)";
						subEl.style.display = "";
					}
				});
								
				contents.map(function(subNode){
					self.rec_node(subEl, subNode);
				});
				
			} else {
				el.innerHTML = text;
				el.style.borderLeft = "1px solid black";
				el.style.paddingLeft = "6px";
				el.style.paddingTop = "5px";
				el.style.paddingBottom = "5px";
				el.style.marginTop = "";
				el.style.marginBottom = "";
			}
			if(parent == root) {
				el.style.borderTop = "";
				el.style.borderBottom = "1px solid black";
				if(elCaret && elCaret.style) {
					elCaret.style.borderLeft = "";
				}
			}
			
			parent.appendChild(el);			
		}
		
		nodes.map(function(node){
			self.rec_node(root, node);
		});
	}
	
	this.init = function() {
		
	}
	
	this.setNodes = function(nods) {
		nodes = nods;
		projectNodes();
		root.style.marginLeft = "-40px";
		root.style.marginTop = "5px";
	}	
	
	this.clear = function() {
		root.innerHTML = "";
	}
	
	this.getElement = function() {
		return root;
	}
	
	self.init();
	return this;
};

window.Projecter.FeedbackCommentPanel = function(comment, codeFmt, projectSelectionsToEditView, setSelectionRange) {
	var self = this;
	self.codeFmt = codeFmt;
	self.common = new Common();
	self.templates = new window.Projecter.Templates();
	self.selections = [];
	
	self.init = function() {
		self.materialize();
	};
	
	self.getNode = function() {
		return self.node;
	}
	
	self.setSelectionRange = setSelectionRange;
	self.materialize = function() {

		var login = comment.login;
		var cdata = comment.config

		var d = self.common.reconstructUI(self.templates.FeedbackLoadedRowUI);
		self.node = d[0];
		var ids = d[1];

		ids.idlogin.innerHTML = login;
		ids.iddate.innerHTML = self.common.convertDateTime(comment.creation_time);
		comment.Editable = comment.login == self.common.identity.login;
		if (!comment.Editable) {
			ids.btnEdit.style.display = "none";
		} else {
			ids.btnEdit.addEventListener("click", function() { // Set to editable field
				projectSelectionsToEditView(comment.config, comment.contents, comment.ID);
			});
		}

		self.selections = cdata.filter(function(item){
			return item && item.mark !==undefined && item.begin !==undefined && item.end !==undefined;
		}).map(function(item){
			return [item.mark, item.begin, item.end]
		});
		self.createCommentElements(ids.ctext, comment.contents);
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
			span.innerHTML = self.codeFmt(txt).split("\n").join("<br />");
			root.appendChild(span);
		};

		var appendSel = function(txt, begin, end) {
			var span = document.createElement("span");
			span.innerHTML = self.codeFmt(txt).split("\n").join("<br />");
			span.style.cursor = "pointer";
			span.style.textDecoration = "underline";
			span.addEventListener("mouseenter", function() {
				self.setSelectionRange(begin, end);
			});
			span.addEventListener("mouseleave", function() {
				//self.setSelectionRange(0, 0);
			});
			span.addEventListener("click", function() {
				self.setSelectionRange(begin, end, true);
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

window.Projecter.LoadedFeedbackPanel = function(data, projectID, codeFmt, reloadCB, setSelectionRangeCB, getSelectionRangeCB) {
	var self = this;
	self.data = data;
	var fileID = self.data.contents.ID;
	
	self.fileID = fileID;
	self.codeFmt = codeFmt;
	self.common = new Common();
	self.templates = new window.Projecter.Templates();
	self.lastEditingCommentID = -1;
	var selectionNode = undefined;
	var commentEditorNode = undefined;
	
	self.init = function() {
		self.materialize();
	};
	
	self.getNode = function() {
		return self.node;
	}
	
	self.editor = {
		totalSelsCnt: 0,
		selections: [],

		setSelectionRange: function(begin, end, focus) {
			setSelectionRangeCB(begin, end, focus);
		},

		getSelectionRange: function() {
			return getSelectionRangeCB();
		},

		addCurentSelection: function(start, end, selID) {
			var sels = self.editor.getSelectionRange();
			var start = start === undefined ? sels[0] : start;
			var end = end === undefined ? sels[1] : end;
			var len = end - start;
			if (len > 0) {
				var selID = selID === undefined ? self.editor.totalSelsCnt : selID
				self.editor.totalSelsCnt = selID + 1;
				var el = document.createElement("div");
				el.style.display = "block";
				el.style.width = "100%";
	
				var selData = [selID, start, end]
				self.editor.selections.push(selData);
	
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
					self.editor.selections = self.editor.selections.filter(function(q) { return q[0] != selData[0]; });
				})
	
				el.appendChild(el3);
	
				el2.innerHTML = "[" + selID + "] (" + len + " " + zn + ")";
				el.style.cursor = "pointer";
	
				el2.addEventListener("mouseenter", function() {
					self.editor.setSelectionRange(start, end);
				});
				el2.addEventListener("mouseleave", function() {
					self.editor.setSelectionRange(0, 0);
				});
	
				el2.addEventListener("click", function() {
					self.editor.setSelectionRange(start, end);
	
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
		}
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

		sels.map(function(sel) { self.editor.addCurentSelection(sel.begin, sel.end, sel.mark); });
		self.commentArea.value = text;
	}
	
	
	self.materialize = function() {
		var d = self.common.reconstructUI(self.templates.commentUI);
		self.node = d[0];
		var ids = d[1];
		
		self.btnSave = ids.btnSave;
		self.btnCancelEdit = ids.btnCancelEdit;
		self.commentSel = ids.commentSel;
		self.commentArea = ids.commentArea;
		self.btnComment = ids.btnComment;

		ids.commentcontents.innerHTML = "";
		
		selectionNode = ids.commentSel
		commentEditorNode = ids.commentEditorNode;
		
		self.commentArea = commentEditorNode;
		
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
			if (comment.login && comment.config && comment.config.length !== undefined && comment.contents !== undefined && comment.creation_time) {
				var cmnt = new window.Projecter.FeedbackCommentPanel(comment, self.codeFmt, self.projectSelectionsToEditView, self.editor.setSelectionRange);
				ids.commentcontents.appendChild(cmnt.getNode());
			}
		});

		self.lastEditingCommentID = -1;
		
		ids.btnCancelEdit.addEventListener("click", self.cancelProjectionToEditView);
		ids.btnSave.addEventListener("click", function() {
			var text = self.commentArea.value.trim();
			self.common.showLoader();
			var cbFailNewComment = function(err) {
				self.common.hideLoader();
				self.common.showError("Chyba", "Nepodařilo se uložit komentář", true, err);
			}
			var cbOkNewComment = function(data) {
				self.common.hideLoader();
				reloadCB();
			}

			var asyncData = {
				"action" : "HANDLE_PROJECTS",
				"project_data" : "edit_feedback",
				"project_id": projectID,
				"file_id": self.fileID,
				"comment_id": self.lastEditingCommentID,
				"selections": self.editor.selections,
				"text": text
			};
			self.common.async(asyncData, cbOkNewComment, cbFailNewComment, false);
		});

		// Create table for comments
		ids.btnAddSel.addEventListener("click", function() { self.editor.addCurentSelection(); })
		ids.btnComment.addEventListener("click", function() {

			var text = self.commentArea.value.trim();
			if (text.length == 0) {
				self.common.showError("Chyba", "Nelze uložit prázdný komentář", true);
				return;
			}

			// Save comment and reload
			self.common.showLoader();
			var cbFailNewComment = function(err) {
				self.common.hideLoader();
				self.common.showError("Chyba", "Nepodařilo se uložit komentář", true, err);
			}
			var cbOkNewComment = function(data) {
				self.common.hideLoader();
				reloadCB();
			}
			var asyncData = {
				"action" : "HANDLE_PROJECTS",
				"project_data" : "add_feedback",
				"project_id": projectID,
				"file_id": self.fileID,
				"selections": self.editor.selections,
				"text": text,
			};
			self.common.async(asyncData, cbOkNewComment, cbFailNewComment, false);
		});

	};
	
	self.handleInitSize = function() {
		self.midcell.style.height = self.leftCell.offsetHeight + "px";
		self.newComTable.style.height = (self.midcell.offsetHeight - self.comTable.offsetHeight) + "px";
	}
	
	self.init();
	return this;
};

window.Projecter.ProjectOpenedFile = function(data, fileData, closeCB, reloadCB) {
	var self = this;
	var focus = false;
	var root = document.createElement("div");
	var pr = document.createElement("pre");
	var code = document.createElement("code");
	self.data = data;
	self.fileData = fileData;
	
	var commentPanelWidth = 400;
	
	root.appendChild(pr);
	pr.appendChild(code);
	
	root.style.padding= "5px"
	
	var selections = {"begin": 0, "end": 0}
	var selectionUndoers = [];
	
	self.undoSel = function() {
		selectionUndoers.map(function(undoer){
			undoer();
		})
		selectionUndoers = [];
	}
	
	self.setHighlightedSelection = function(begin, end, focus) {
		var length = end-begin;
		if(length > 0) {
			selections.begin = begin;
			selections.end = end;
		} else {
			// Clear selection
			selections.begin = 0;
			selections.end = 0;
		}
		self.undoSel(); // Remove everything that could still be there
		
		// Find nodes that cover
		
		var getAllTexted = function(root, res) {
			if(root && root.childNodes && root.childNodes.length > 0) {
				for(var i = 0, o = root.childNodes.length; i < o; i++) {
					var child = root.childNodes[i];
					res = getAllTexted(child, res);
				}
			} else if(root.textContent) {
				res.push({"length": root.textContent.length, "text": root.textContent, "node": root});				
			} else if(root.innerText) {
				res.push({"length": root.innerText.length, "text": root.innerText, "node": root});
			}  
			return res;
		}
		
		var getCovered = function(root, index, length) {
			var texted = getAllTexted(root, []);
			
			var cumSum = 0;
			return texted.map(function(item) {
				item.begin = cumSum;
				cumSum += item.length
				return item;
			}).filter(function(item) {
				return item.begin + item.length >= index && item.begin < index + length;
			}).map(function(item){
				var node = item.node;
				if(node.style === undefined) { // Text node ?
					var subParent = document.createElement("span");
					var originalNode = item.node;
					subParent.innerHTML = item.text
					item.node.parentElement.insertBefore(subParent, item.node);
					item.node.parentElement.removeChild(item.node);
					item.node = subParent;
					selectionUndoers.push(function(){
						subParent.parentElement.insertBefore(originalNode, subParent);
						subParent.parentElement.removeChild(subParent);
					});
				}
				return item;
			});
		}
		
	
		var splitNode = function(item, splitLength) {
			var first = document.createElement("span");
			var second = document.createElement("span");
			var txt = item.text;
			var firstText = txt.substr(0, splitLength);
			var secondText = txt.substr(splitLength);
			first.innerHTML = firstText;
			second.innerHTML = secondText;
			var originalInnerHTML = item.node.innerHTML;
			item.node.innerHTML = "";
			item.node.appendChild(first);
			item.node.appendChild(second);
			selectionUndoers.push(function(){
				item.node.innerHTML = "";
				item.node.innerHTML = originalInnerHTML;
			});
			
			return [first, second, firstText, secondText];
		}
		
		var getAffected = function() {
			var covered = getCovered(pr, selections.begin, selections.end - selections.begin);
			if(covered.length >= 1) {
				var item = covered[0];
				
				if(item.begin < begin) {
					var nd = splitNode(item, begin - item.begin);
					item.node = nd[1];
					item.length = item.length - (begin - item.begin);
					item.begin = begin;
					item.text = nd[3];
				}
				
				covered[0] = item;
				
				item = covered[covered.length - 1];
				
				// Starts at begin, cut to length
				if(item.begin + item.length > end) {
					
					var nd = splitNode(item, item.length - ((item.length - end) + item.begin));
					item.node = nd[0];
					item.length = item.length - ((item.length - end) + item.begin);
					item.text = nd[2];
				}
				covered[covered.length - 1] = item;
			}	
			return covered;
		}
		focus=true;
		
		var affected = getAffected();
		for(var i = 0; i < affected.length; i++) {
			var first = i == 0;
			var last = i == affected.length - 1;
			var item = affected[i].node;
			
			var savedProperties = ["background", "borderTop", "borderBottom", "borderLeft", "borderRight"];
			var savedValues = savedProperties.map(function(prop){
				return [prop, item.style[prop]];
			})
			selectionUndoers.push(function(){
				savedValues.map(function(entry){
					var prop = entry[0];
					var val = entry[1];
					item.style[prop] = val;
				})
			});
			
			item.style.background = "#aaffee";
			item.style.borderTop = "1px solid black";
			item.style.borderBottom = "1px solid black";
			if(first) {
				item.style.borderLeft = "1px solid black";
			}
			if(last) {
				item.style.borderRight = "1px solid black";
			}
			if(focus) {
				item.scrollIntoView({"behavior": "smooth", "block": "nearest" });
				item.focus();
			}
		}
	}
	
	self.getSelection = function() {
		var sel = document.getSelection();
		var isOur = function(node) {
			if(node == code) {
				return true;
			} else if (node && node.parentElement) {
				return isOur(node.parentElement);
			} else {
				return false;
			}
		}
		
		var getLengthOfPreviousSiblings = function(node) {
			var res = "";
			while(node && node.previousSibling) {
				node = node.previousSibling;
				res = (node.innerText ? node.innerText : node.textContent ? node.textContent : "") + res ;
			}
			return res;
		}
		
		var getPriorOffset = function(node) {
			if(node == code) {
				return "";
			} else {
				return getPriorOffset(node.parentElement) + getLengthOfPreviousSiblings(node);
			}
		}
		
		var toOffset = function(text) {
			var originalText = atob(fileData.contents.contents);
			var textOffset = 0;
			var maxOffset = text.length
			for(var i = 0, o = originalText.length; i < o; i++) {
			    var chr = originalText.charAt(i);
				var tchr = text.charAt(textOffset);
				if(chr == tchr) {
					textOffset++;
					if(textOffset == maxOffset) {
						return textOffset;
					}
				} else if(chr == ' ' || chr == '\t' || chr == '\n' || chr == '\r') {
					continue;
				} else {
					break;
				}
			}
			console.error("Failed to get selection");
			return 0;
		}
		
		if(sel && isOur(sel.anchorNode) && isOur(sel.extentNode) && sel.rangeCount == 1) {
			var range = sel.getRangeAt(0);
			var preText = getPriorOffset(range.startContainer);
			var postText = getPriorOffset(range.endContainer);
			
			var preOffset = toOffset(preText);
			var postOffset = toOffset(postText);
			
			
			var begin = preOffset + range.startOffset
			var end = postOffset + range.endOffset
			
			self.setHighlightedSelection(begin, end);
			return [begin, end]
		}
		return [0, 0];
	}
	
	var getEditor = function(fileName, editors) {
		if(fileName && editors) {
			if (fileName in editors) {
				return editors[fileName];
			} else { // Could be regex
				for(var editorFile in editors) {
					if(editors.hasOwnProperty(editorFile)) {
						var editor = editors[editorFile];
						if(editorFile.length > 2 && editorFile.substr(0, 1) =="/" && editorFile.substr(-1, 1) == "/") { // Regex
							var re = editorFile.substr(1, editorFile.length - 2);
							re = new RegExp(re);
							if(fileName.match(re)) {
								return editor;	
							}
						}
					}
				}
			}
		}
		return "none";
	}
	
	var editor = getEditor(fileData.file.name, data.config.editor_files);
	var commentable = data.config.comment_files.reduce(function(total, item) {return total || fileData.file.name == item}, false);
	
	var fmt = function (code) {
		if(editor == "common_hex") {
			var el = document.createElement("span");
			var forLine = function(line) {
				var mp = [];
				line.split("").map(function(c) {
					if(c == '\r') {
						c = "\\r";
					} else if(c == '\t'){
						c = "\\t";
					}
					var x = document.createElement("span");
					x.style.display = "inline-block";
					x.style.borderLeft = "1px solid black";
					x.style.borderRight = "1px solid black";
					x.style.borderTop = "1px solid black";
					x.style.borderBottom = "1px solid black";
					x.style.padding = "2px";
					x.innerHTML = c;
					x.style.fontFamily = "Courier";
					mp.push(x);
				});
				return mp;
			}
			var reducer = function(all, item) {
				all.push(item);
				return all;
			}
			
			var data = code.split("\n").map(forLine).reduce(reducer, []);
			
			for(var y = 0; y < data.length; y++) {
				var line = data[y];
				for(var x = 0; x < line.length; x++) {
					var cellAbove = y > 0 && data[y - 1].length - 1 >= x;
					var bl = x == 0;
					var br = true;
					var bt = (y == 0) || !cellAbove
					var bb = true;
					data[y][x].style.borderLeft = bl ? "1px solid black": "";
					data[y][x].style.borderRight = br ? "1px solid black": "";
					data[y][x].style.borderTop = bt ? "1px solid black": "";
					data[y][x].style.borderBottom = bb ? "1px solid black": "";
					el.appendChild(data[y][x]);
				}
				el.appendChild(document.createElement("br"));
			}
			return el;
		} else {
			var el = document.createElement("span");
			el.innerHTML = code;
			return el;
		}
	}
	
	if(editor == "common_c") {
		code.classList.add("language-c");
		var wrapFmt = fmt;
		fmt = function (code) {
			return wrapFmt(Prism.highlight(code, Prism.languages.clike, 'javascript'));
		}
	}
	
	
	self.init = function() {
		if(editor == "none") {
			return;
		}
		var contents = atob(fileData.contents.contents);
		if(contents) {
			code.innerHTML = "";
			code.appendChild(fmt(contents));
			
		} else {
			closeCB();
		}
	
		
	}
	
	self.getElement = function() {
		return root;
	}
	
	var originalWidth = "";
	var originalBorderRight = "";
	var originalScrollLeft = 0;
	var originalScrollTop = 0;
	
	var commenter = new window.Projecter.LoadedFeedbackPanel(self.fileData, data.ID, function(x){return x;}, reloadCB, self.setHighlightedSelection, self.getSelection);
	var commenterNode = commenter.getNode();
	
	
	var newlyFocused = function() {
		root.style.display = "block";
		originalWidth = root.parentElement.style.width;
		originalBorderRight = root.parentElement.style.borderRight;
		root.parentElement.scrollLeft = originalScrollLeft ;
		root.parentElement.scrollTop = originalScrollTop;
		if(commentable) {
			root.parentElement.style.width = "calc(90% - " + (commentPanelWidth + 290) + "px)";
			root.parentElement.style.borderRight = "1px solid black";
			root.parentElement.parentElement.appendChild(commenterNode);
		} else {
			root.parentElement.style.width = originalWidth;
			root.parentElement.style.borderRight = originalBorderRight;
			if(commenterNode.parentElement) {
				commenterNode.parentElement.removeChild(commenterNode);
			}			
		}
	}
	
	var newlyUnfocused = function() {
		root.parentElement.style.width = originalWidth;
		root.parentElement.style.borderRight = originalBorderRight;
		originalScrollLeft = root.parentElement.scrollLeft;
		originalScrollTop = root.parentElement.scrollTop;
		if(commenterNode.parentElement) {
			commenterNode.parentElement.removeChild(commenterNode);
		}
		root.style.display = "none";
	}
	
	self.updateComments = function(newComments) {
		if(commentable) {
			fileData.comments = newComments;
			self.fileData = fileData;
			if(commenterNode && commenterNode.parentElement) {
				commenterNode.parentElement.removeChild(commenterNode);
			}
			commenter = new window.Projecter.LoadedFeedbackPanel(self.fileData, data.ID, function(x){return x;}, reloadCB, self.setHighlightedSelection, self.getSelection);
			commenterNode = commenter.getNode();
			root.parentElement.parentElement.appendChild(commenterNode);
		}
	}
	
	self.close = function() {
		if(root.parentElement) {
			self.loseFocus();
			root.parentElement.removeChild(root);
		}
	}
	
	self.gainFocus = function() {
		if(!focus) {
			newlyFocused();
		}
		focus = true;
	}
	
	self.hasFocus = function() {
		return focus;
	}
	
	self.loseFocus = function() {
		if(focus) {
			newlyUnfocused();
		}
		focus = false;
	}
	
	self.init();
	return this;
}

window.Projecter.ProjectResults = function(data) {
	var self = this;
	self.data = data;
	self.common = new Common();
	self.templates = new window.Projecter.Templates();
	self.root = null;
	
	self.init = function() {
		self.filesUI = new window.Projecter.Tree();
		
		var d = self.common.reconstructUI(self.templates.cardResults);
		var el = d[0];
		var ids = d[1];
		
		ids.pnlContent.appendChild(self.filesUI.getElement());
		self.pnlEditors = ids.pnlEditors;
		self.pnlTabs = ids.pnlTabs;
		
		self.root = el;
	}
	
	self.getElement = function() {
		return self.root;
	}
	
	var openedFiles = {};
	
	var addOpenedFileTab = function(file, openCB, closeCB) {
		var name = file.ownerLogin + "/" + file.remappedName;
		var el = document.createElement("div")
		var defColor = "#ffffaa";
		el.style.display = "inline-block";
		el.style.borderRight = "1px solid black";
		el.style.paddingLeft = "20px";
		el.style.paddingRight = "5px";
		el.style.cursor = "pointer";
		el.style.userSelect = "none";
		el.style.paddingTop = "10px";
		el.style.paddingBottom = "10px";
		el.style.background = defColor;
		
		var lbl = document.createElement("div");
		var clsBtn= document.createElement("span");
		lbl.innerHTML = name;
		clsBtn.innerHTML = "[X]";
		clsBtn.style.marginLeft = "30px"
		
		lbl.style.display = "inline-block";
		clsBtn.style.display = "inline-block";
		
		el.appendChild(lbl);
		el.appendChild(clsBtn);
		
		
		self.pnlTabs.appendChild(el);
		var isActive = false;
		
		el.addEventListener("click", function(){
			if(el.parentElement) {
				openCB();
			}
		})
		
		var closer = function() {
			if(el.parentElement) {
				el.parentElement.removeChild(el);
			}
		}
		
		clsBtn.addEventListener("click", function(){
			var wasActive = isActive;
			closer();			
			closeCB(wasActive);
		})
		
		var selector = function(selected) {
			isActive = selected;
			el.style.background = selected ? "#aaffff" : defColor
		}
		
		return [closer, selector]
	}
	
	var openFile = function(file, login, remappedName) {
		file.ownerLogin = login;
		file.remappedName = remappedName ? remappedName : file.name;
		self.common.showLoader();
		var cbFail = function(err) {
			self.common.hideLoader();	
			self.common.showError("Chyba načítání dat", "Nepodařilo se nahrát soubor", true, err);
		}
		var cbOK = function(data) {
			self.common.hideLoader();
			if(data && data.err) {
				cbFail(data.err);
				return;
			} else if(data && data.data) {
				var fileKey = "file_" + file.id;
				
				var removeKey = function(object, key) {
					var newObject = {}
					for(var k in object){
						if(object.hasOwnProperty(k) && k != key) {
							newObject[k] = object[k];
						}
					}
					return newObject;
				}
				
				var openOurEditor = function() {
					for(var anotherKey in openedFiles) {
						if(openedFiles.hasOwnProperty(anotherKey) && anotherKey != fileKey) {
							openedFiles[anotherKey].editor.loseFocus();
							openedFiles[anotherKey].tabSetSetSelected(false); 
						}
					}
					if(fileKey in openedFiles) {
						openedFiles[fileKey].editor.gainFocus();
						openedFiles[fileKey].tabSetSetSelected(true);
					}			
				}
				
				var openNextEditor = function() {
					var prev = undefined;
					var cnt = false;
					for(var xk in openedFiles) {
						if(openedFiles.hasOwnProperty(xk)) {
							if(xk == fileKey) {
								if(prev) {
									break;
								} else {
									cnt = true;
								}
							} else if(cnt) {
								prev = xk;
								break; 
							} else {
								prev = xk;
							}
						}
					}
					return function(){
						if(prev) {
							openedFiles[prev].editor.gainFocus();
							openedFiles[prev].tabSetSetSelected(true);
						}	
					}
				}
				
				if(!(fileKey in openedFiles)) {
					var tabData = {};
					tabData.file = file;
					tabData.contents = data.data;
					tabData.comments = data.comments;
					tabData.editor = new window.Projecter.ProjectOpenedFile(self.data, {"file": file, "contents": data.data, "comments": data.comments}, function() {
						if(tabData.tabRemover) {
							tabData.tabRemover();
						}
						var fn = openNextEditor();
						openedFiles = removeKey(openedFiles, fileKey);
						fn();
					}, function(){
						/* AKA reloadCB  */
						var cbFail = function(err){
							self.common.hideLoader();
							self.common.showError("Chyba načítání dat", "Nepodařilo se aktualizovat obsah souboru", true, err);
						}
						var cbOK = function(data) {
							if(data && data.comments) {
								self.common.hideLoader();
								tabData.editor.updateComments(data.comments);
								return;	
							}
							cbFail("Neplatná struktura ze serveru");
						}
						
						var asyncData = {
							"action" : "HANDLE_PROJECTS",
							"project_data" : "loadFile",
							"project_id": self.data.ID,
							"file_id": file.id
						};
						self.common.showLoader();
						self.common.async(asyncData, cbOK, cbFail, true);
						
					});
					var tabbers = addOpenedFileTab(file, openOurEditor, function(wasActive) {
						tabData.editor.close();
						var fn = openNextEditor();
						openedFiles = removeKey(openedFiles, fileKey);
						if(wasActive) {
							fn();
						}
					});
					tabData.tabRemover = tabbers[0];
					tabData.tabSetSetSelected = tabbers[1];
					openedFiles[fileKey] = tabData;
					self.pnlEditors.appendChild(tabData.editor.getElement());
				}
				openOurEditor();
				return;
			}
			cbFail("Neplatná odpověď serveru");
		}
		self.common.showLoader();
		var asyncData = {
			"action" : "HANDLE_PROJECTS",
			"project_data" : "loadFile",
			"project_id": self.data.ID,
			"file_id": file.id
		};
		self.common.async(asyncData, cbOK, cbFail, true);
	}
	
	var smartObject = function(delim) {
		var nself = this;
		nself.data = {};
		nself.IsSmartObject = true;
		
		nself.set = function(key, value) {
			var keys = key.split(delim);
			if(keys.length == 1) {
				if(key in nself.data) {
					if(!nself.data[key].push) {
						nself.data[key] = [nself.data[key]];
					}
					nself.data[key].push(value);
				} else {
					nself.data[key] = value;
				}
			} else {
				var key = keys[0];
				var val = new smartObject(delim);
				if(key in nself.data) {
					if(nself.data[key].IsSmartObject) {
						val = nself.data[key]; 						
					}
				}
				val = val.set(keys.slice(1).join(delim), value);
				nself.data[keys[0]] = val;
			}
			return this; 
		};
		
		nself.toDumbObject = function() {
			var x = {};
			for(var k in nself.data) {
				if(nself.data.hasOwnProperty(k)) {
					var v = nself.data[k];
					if(v && v.IsSmartObject) {
						v = v.toDumbObject();	
					}
					x[k] = v;
				}
			}
			return x;
		};
		
		nself.toFilesObject = function(sorter) {
			var x = [];
			for(var k in nself.data) {
				if(nself.data.hasOwnProperty(k)) {
					var v = nself.data[k];
					if(v && v.IsSmartObject) {
						v = v.toFilesObject(sorter);	
					}
					if(v && v.text) {
						x.push(v)
					} else  {
						x.push({"text": k, "contents": v})
					}
				}
			}
			return x.sort(sorter);
		}
		
		return this;
	};
	
	var loadFiles = function() {
		self.filesUI.clear();
		
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showError("Chyba načítání dat", "Nepodařilo se nahrát soubory", true, err);
		}
		var cbOK = function(data) {
			self.common.hideLoader();
			var fileMapping = self.data.config.files;
			var fileMappingIndexes = {};
			var index = 0;
			for(var key in fileMapping) {
				if(fileMapping.hasOwnProperty(key)) {
					fileMappingIndexes[key] = index;
					index++;
				}
			}
			var sorter = function(aa, bb) {
				var a = aa.text;
				var b = bb.text;
				if(aa.originalText) {
					a = aa.originalText;
				}
				if(bb.originalText) {
					b = bb.originalText;
				}
				if(aa.contents !== undefined && bb.contents === undefined) {
					return -1;
				} else if(aa.contents === undefined && bb.contents !== undefined) {
					return 1;
				} else if(aa.contents !== undefined && bb.contents !== undefined) {
					return a.localeCompare(b);
				} else if(a in fileMapping && b in fileMapping) {
					a = fileMappingIndexes[a];
					b = fileMappingIndexes[b];
					return a > b ? 1 : a < b ? -1 : 0; 
				} else if(a in fileMapping) {
					return -1
				} else if(b in fileMapping) {
					return 1
				} else {
					return a.localeCompare(b);
				}
			}
			
			if(data && data.length !== undefined) {
				var allFiles = new smartObject("/");
				data.map(function(item){
					var config = item.data;
					var files = item.files;
					var login = config.login;
					files.map(function(file) {
						var name = file.name;
						if(name in fileMapping) {
							name = fileMapping[name];
						}
						allFiles.set(login + "/" + name, {"text": name, "originalText": file.name, "click": function() {
							openFile(file, login, name);
						}});
					});
				});
				allFiles = allFiles.toFilesObject(sorter);
				self.filesUI.setNodes(allFiles);
				return;
			}
			return cbFail("Neplatná odpověď serveru");
		}
		self.common.showLoader();
		var asyncData = {
			"action" : "HANDLE_PROJECTS",
			"project_data" : "loadFiles",
			"project_id": data.ID
		};
		self.common.async(asyncData, cbOK, cbFail, true);	
	}
	
	self.newlyOpened = function() {
		loadFiles();
	}
	
	self.init();
	
	return this;
}

window.Projecter.ProjectInput = function(data) {
	var self = this;
	self.data = data;
	self.common = new Common();
	self.templates = new window.Projecter.Templates();
	self.root = null;
	
	var reloadFiles = function() {
		self.pnlExisting.innerHTML = "";
		
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showError("Chyba načítání dat", "Nepodařilo se nahrát soubory", true, err);
		}
		var cbOK = function(data) {
			self.common.hideLoader();
			
			var first = true;
			if(data && data.length !== undefined) {
				data.map(function(item){
					var config = item.data;
					var files = item.files;
					
					var login = config.login;
					var uploader = config.config.creator;
					
					files.map(function(file){
					
						var d = self.common.reconstructUI(self.templates.cardInputsExistingUI);
						var el = d[0];
						var ids = d[1];
	
						if(!first) {
							el.style.borderTop = "1px solid black";
						}
						first = false;
						
						
						ids.txtStudent.innerHTML = login + "/" + file.name;
						ids.txtUploader.innerHTML = uploader;
						
						self.pnlExisting.appendChild(el);
					});
				});
				
				return;
			}
			return cbFail("Neplatná odpověď serveru");
		}
		self.common.showLoader();
		var asyncData = {
			"action" : "HANDLE_PROJECTS",
			"project_data" : "loadFiles",
			"project_id": data.ID
		};
		self.common.async(asyncData, cbOK, cbFail, true);	
	}
	
	var handleFile = function(file) {
		var processFiles = function(files) {
			var filesByLogin = {};
			
			var requiredPrefix = self.data.config.prefix;
			var storedFiles = self.data.config.files;
			
			var isStoredFile = function(name) {
				return name in storedFiles;
			}
			
			var startsWith = function(str, prefix) {
				return str.substr(0, prefix.length) == prefix;
			}
			
			files.map(function(file) {
				if(startsWith(file.name, requiredPrefix)) {
					var path = file.name.substr(requiredPrefix.length);
					if(path.substr(0,1) == "x") { // Student file
						var login = path.substr(0, path.indexOf("/"));
						if(!(login in filesByLogin)) {
							filesByLogin[login] = {};
						}
						var name = path.substr(path.indexOf("/") + 1);
						if(isStoredFile(name)) {
							filesByLogin[login][name] = file.contents;
						}
					}
				}
			});
			
			// Save files
			var cbFail = function(err) {
				self.common.hideLoader();
				self.common.showError("Chyba načítání dat", "Nepodařilo se uložit některé soubory", true, err).then(function(){
					reloadFiles();
				});
			}
			var cbOK = function(data) {
				self.common.hideLoader();
				if(data) {
					reloadFiles();
					return;
				}
				return cbFail("Neplatná odpověď serveru");
			}
			self.common.showLoader();
			var asyncData = {
				"action" : "HANDLE_PROJECTS",
				"project_data" : "updateFiles",
				"project_id": data.ID,
				"files": filesByLogin
			};
			self.common.async(asyncData, cbOK, cbFail, true);
		}
		
		
		var reader = new FileReader();
		reader.onload = function (event) {
			var raw = event.target.result;
			var zip = new JSZip();
			zip.loadAsync(raw).then(function(zipper){
				var filesByName = {};
				
				var fea = function(zipFile) {
					var name = zipFile.name;
					if(!zipFile.dir) {
						return zipFile.async('base64').then(function(content) {
							filesByName[name] = content;
						});
					}
					return false;
				}
				
				var promises = [];
				for(var zipFileKey in zipper.files) {
					if(zipper.files.hasOwnProperty(zipFileKey)) {
						var p = fea(zipper.files[zipFileKey]);
						if(p) {
							promises.push(p);
						}
					}
				}
				Promise.all(promises). then(function(){
					var files = [];
					for(var fileName in filesByName){
						if(filesByName.hasOwnProperty(fileName)){
							files.push({"name": fileName, "contents": filesByName[fileName]});
						}
					}
					processFiles(files);
				});
			});
		};
		reader.readAsArrayBuffer(file);
	}
	
	var setupDragDrop = function(element) {
		element.addEventListener("dragenter", function(e){
			e.preventDefault();
  			e.stopPropagation();
			element.style.background = "#aaaaaa";
		}, false);
		element.addEventListener("dragleave", function(e){
			e.preventDefault();
  			e.stopPropagation();
			element.style.background = "";
		}, false);
		element.addEventListener("dragover", function(e){
			e.preventDefault();
			e.stopPropagation();
			element.style.background = "#aaaaaa";
		}, false);
		element.addEventListener("drop", function(e){
			e.preventDefault();
			e.stopPropagation();
			element.style.background = "";
			var files = e.dataTransfer.files;
			for(var i = 0; i < files.length; i++) {
				handleFile(files[i]);
			}
		}, false);
	}
	
	self.init = function() {
		var d = self.common.reconstructUI(self.templates.cardInputs);
		var el = d[0];
		var ids = d[1];
		
		setupDragDrop(ids.pnlUpload);
		
		self.pnlExisting = ids.pnlExisting;
		
		self.root = el;
	}
	
	self.getElement = function() {
		return self.root;
	}
	
	self.newlyOpened = function() {
		reloadFiles();
	}
	
	self.init();
	
	return this;
}

window.Projecter.Project = function(data, reloadCB) {
	var self = this;
	self.common = new Common();
	self.templates = new window.Projecter.Templates();
	self.root = document.createElement("div");
	self.pnlSettings = null;
	self.pnlResults = null;
	self.pnlInput = null;
	
	
	self.init = function() {
		self.reconstructUI();
	}

	var linkPanel = function(ids, btn, pnl) {
		var setVisible = function(pnl, vis) {
			var el = pnl.getElement();
			if(el.parentElement && !vis) {
				el.parentElement.removeChild(el);
			} else if(vis && !el.parentElement) {
				ids.pnls.appendChild(el);
				pnl.newlyOpened();
			}
		};
		var open = function() {
			var el = pnl.getElement();
			if(!el.parentElement) {
				setVisible(self.pnlSettings, false);
				setVisible(self.pnlResults, false);
				setVisible(self.pnlInput, false);
				setVisible(pnl, true);
			}
		}
		btn.addEventListener("click", open);
	}
	


	self.reconstructUI = function() {
		var d = self.common.reconstructUI(self.templates.projectUI);
		var el = d[0];
		var ids = d[1];
		
		ids.pnlTitle.innerHTML = data.config.title;
		self.root.appendChild(el);
		
		var noAdminCB = function() {
			ids.card_set_cardBtn.style.display = "none";
			ids.card_io_cardBtn.style.display = "none";
			ids.card_res_cardBtn.style.borderLeft = "";
			ids.card_res_cardBtn.style.borderRight = "1px solid black";
			ids.card_res_cardBtn.innerHTML = "Hodnocení";
			ids.card_res_cardBtn.click();
		}
		
		self.pnlSettings = new window.Projecter.ProjectSettings(data, reloadCB, noAdminCB);
		self.pnlResults = new window.Projecter.ProjectResults(data, reloadCB, noAdminCB);
		self.pnlInput = new window.Projecter.ProjectInput(data, reloadCB, noAdminCB);
		
		linkPanel(ids, ids.card_set_cardBtn, self.pnlSettings);
		linkPanel(ids, ids.card_res_cardBtn, self.pnlResults);
		linkPanel(ids, ids.card_io_cardBtn, self.pnlInput);
		ids.card_res_cardBtn.click();
	}	
	
	self.getElement = function() {
		return self.root;
	}
	
	self.init();
	return this;
}

window.Projecter.Main = function() {
	var self = this;
	self.common = new Common();
	self.templates = new window.Projecter.Templates();
	self.allProjects = [];
	self.root = document.createElement("div");
	
	var reloadCB = function() {
		self.common.showLoader();
		self.loadProjects(self.common.hideLoader);
	}
	
	self.loadProjects = function(hideLoaderFN) {
		var cbFail = function(err) {
			hideLoaderFN();
			self.common.showError("Chyba načítání dat", "Nepodařilo se nahrát seznam projektů", true, err);
		}
		var cbOK = function(data) {
			hideLoaderFN();
			self.allProjects = [];
			self.root.innerHTML = "";
			if(data && data.projects && data.projects.map !== undefined) {
				data.projects.map (function(proj){
					var p = new window.Projecter.Project(proj, reloadCB);
					self.allProjects.push(p);
					self.root.appendChild(p.getElement())
				});
				self.common.setLoginPanelVisible(true);
				return;
			}
			cbFail("Neplatná odpověď serveru");
		}
		
		var asyncData = {
			"action" : "HANDLE_PROJECTS",
			"project_data" : "getData"
		};
		self.common.async(asyncData, cbOK, cbFail, true);	
	}
	
	self.init = function() {
		document.body.appendChild(self.root);
		self.common.addLoginPanel();
		self.common.setLoginPanelVisible(false);
		
		if(window.pastAload) {
			window.pastAload();
		}
		self.common.showInitLoader("Načítám projekty", "#44ff44");
		self.loadProjects(self.common.hideInitLoader);
	}
	
	
	self.init();
	return this;
}

function aload() {
	new window.Projecter.Main();
}

window.inject("projects/templates.js");
window.inject("WEB.ADMIN", "admin.js");