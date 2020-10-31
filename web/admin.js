window.mobileAndTabletCheck = function() {
	let check = false;
	(function(a) {
		if (/(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino|android|ipad|playbook|silk/i.test(a)
				|| /1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\-(n|u)|c55\/|capi|ccwa|cdm\-|cell|chtm|cldc|cmd\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\-s|devi|dica|dmob|do(c|p)o|ds(12|\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\-|_)|g1 u|g560|gene|gf\-5|g\-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd\-(m|p|t)|hei\-|hi(pt|ta)|hp( i|ip)|hs\-c|ht(c(\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\-(20|go|ma)|i230|iac( |\-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc\-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|\-[a-w])|libw|lynx|m1\-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\-2|po(ck|rt|se)|prox|psio|pt\-g|qa\-a|qc(07|12|21|32|60|\-[2-7]|i\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\-|oo|p\-)|sdk\/|se(c(\-|0|1)|47|mc|nd|ri)|sgh\-|shar|sie(\-|m)|sk\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\-|v\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\-|tdg\-|tel(i|m)|tim\-|t\-mo|to(pl|sh)|ts(70|m\-|m3|m5)|tx\-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\-|your|zeto|zte\-/i
						.test(a.substr(0, 4)))
			check = true;
	})(navigator.userAgent || navigator.vendor || window.opera);
	return check;
};

var Admin = function(givenCommon) {

	var self = this;
	if(givenCommon) {
		self.common = givenCommon;
	} else {
		self.common = new Common();
	}

	self.originalUIVisibilities = {};

	self.getIcoForExtension = function(ext) {
		if (ext == "cfg") {
			return "cfg";
		} else if (ext == "db") {
			return "db";
		} else if (ext == "file") {
			return "file";
		} else if (ext == "folder") {
			return "folder";
		} else if (ext == "ini") {
			return "ini";
		} else if (ext == "json") {
			return "json";
		} else if (ext == "stream") {
			return "stream";
		} else if (ext == "table") {
			return "table";
		} else if (ext == "thread") {
			return "thread";
		} else if (ext == "view") {
			return "view";
		} else if (ext == "html") {
			return "html";
		} else if (ext == "js") {
			return "js";
		} else if (ext == "css") {
			return "css";
		} else if (ext == "php") {
			return "php";
		} else if (ext == "exe") {
			return "exe";
		} else { 
			return "file";
		}
	}

	self.UI = {
	  "type": "table",
	  "class": "adm_table_main",
	  "contents": [
	    {
	      "type": "tr",
	      "class": "adm_tr_main",
	      "contents": [
	        {
	          "type": "td",
	          "colSpan": 2,
	          "contents": [
	            {
	              "type": "Button",
	              "innerHTML": "Zavřít",
	              "class": "adm_td_main_header_closeButton",
	              "id": "btnCloseAdmin"
	            }
	          ]
	        }
	      ]
	    },
	    {
	      "type": "tr",
	      "class": "adm_tr_main_full",
	      "contents": [
	        {
	          "type": "td",
	          "class": "adm_td_main_left",
	          "id": "cellLeft",
	          "contents": [
	            {
	              "type": "center",
	              "contents": [
	                {
	                  "type": "button",
	                  "innerHTML": "Reload",
	                  "id": "btnReload"
	                },
	                {
	                  "type": "br"
	                },
	                {
	                  "type": "div",
	                  "class": "pnlCurrentPath",
	                  "id": "pnlCurrentPath"
	                }
	              ]
	            }
	          ]
	        },
	        {
	          "type": "td",
	          "class": "adm_td_main_right",
	          "rowSpan": 2,
	          "contents": [
	            {
	              "type": "div",
	              "class": "adm_right_top",
	              "id": "adm_right_top",
	              "contents": [
	                {
	                  "type": "center",
	                  "contents": [
	                    {
	                      "type": "span",
	                      "id": "adm_right_top_lbl",
	                      "innerHTML": "test"
	                    }
	                  ]
	                }
	              ]
	            },
	            {
	              "type": "div",
	              "class": "adm_right_mid",
	              "contents": [
	                {
	                  "type": "table",
	                  "class": "adm_content_table",
	                  "id": "adm_content_table"
	                },
	                {
	                  "type": "textarea",
	                  "class": "adm_content_editble",
	                  "id": "adm_content_editble"
	                },
	                {
	                  "type": "div",
	                  "id": "adm_content_details",
	                  "class": "adm_content_details"
	                }
	              ]
	            },
	            {
	              "type": "div",
	              "class": "adm_right_bottom",
	              "contents": [
	                {
	                  "type": "Button",
	                  "innerHTML": "Return",
	                  "id": "btnReturn"
	                },
	                {
	                  "type": "Button",
	                  "innerHTML": "View",
	                  "id": "btnEditView"
	                },
	                {
	                  "type": "Button",
	                  "innerHTML": "Close",
	                  "id": "btnClose"
	                },
	                {
	                  "type": "Button",
	                  "innerHTML": "Save",
	                  "id": "btnSave"
	                },
	                {
	                  "type": "Button",
	                  "innerHTML": "Save and close",
	                  "id": "btnSaveClose"
	                }
	              ]
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
	          "class": "adm_td_main_left_bottom",
	          "contents": [
	            {
	              "type": "div",
	              "class": "adm_td_main_left_bottom_div",
	              "id": "filesPnl"
	            },
	            {
	              "type": "div",
	              "class": "adm_td_main_left_bottom_div_nf",
	              "contents": [
	                {
	                  "type": "input",
	                  "id": "adm_td_main_left_bottom_nf_name"
	                },
	                {
	                  "type": "button",
	                  "id": "adm_td_main_left_bottom_nf_btn",
	                  "innerHTML": "Create"
	                },
	                {
	                  "type": "div",
	                  "class": "adm_waiter",
	                  "id": "waiter"
	                }
	              ]
	            }
	          ]
	        }
	      ]
	    }
	  ]
	};


	self.createUI = function() {
		var struct = self.common.reconstructUI(self.UI);
		var el = struct[0];
		var ids = struct[1];

		self.mainUI = el;
		self.closeBtn = ids["btnCloseAdmin"];
		self.btnReload = ids["btnReload"];
		self.filesPnl = ids["filesPnl"];
		self.editTable = ids["adm_content_table"];
		self.editArea = ids["adm_content_editble"];

		self.pnlCurrentPath = ids["pnlCurrentPath"];
		self.txtNewPath = ids["adm_td_main_left_bottom_nf_name"];
		self.btnNewPathCreate = ids["adm_td_main_left_bottom_nf_btn"];

		self.btnReturn = ids["btnReturn"];
		self.btnEditView = ids["btnEditView"];
		self.btnClose = ids["btnClose"];
		self.btnSave = ids["btnSave"];
		self.btnSaveClose = ids["btnSaveClose"];
		self.fileLbl = ids["adm_right_top_lbl"];
		self.pnlDetails = ids["adm_content_details"];
		self.waiterObj = ids["waiter"];

		document.body.appendChild(self.mainUI);
		self.closeBtn.addEventListener("click", self.closeUI);
		self.btnReload.addEventListener("click", self.navigator.reloadFiles);

		self.btnNewPathCreate.addEventListener("click", self.createFile);

		window.cols = ids["cols"]

		self.originalUIVisibilities["admin_mainUI"] = self.mainUI.style.display;

	}

	self.setWaiterVisible = function(visible) {
		if (visible) {
			self.waiterObj.style.display = "block";
		} else {
			self.waiterObj.style.display = "none";
		}
	}

	self.createFile = function() {
		var path = self.txtNewPath.value.trim();
		if (path.substr(0, 1) == "/") {
			path = path.substr(1);
		}
		if (path == "") {
			return;
		}
		self.async("create", {"name":  path}, function(data) {
			self.navigator.reloadFiles();
			self.txtNewPath.value = "";
		}, function(err) {
			self.common.showError("Chyba", "Nepodařilo se kontaktovat server", true, err);
		}, false)
	}

	self.navigatorUpdate = function() {
		var newPath = "/" + self.navigator.currentWorkingDir;
		self.pnlCurrentPath.innerHTML = newPath;
		var newFiles = self.navigator.getFilesInCurrentDirectory();

		// Clear current files
		self.filesPnl.innerHTML = "";

		var constr = function(nf) {
			var newFile = newFiles[i];
			var el = document.createElement("div");
			el.style.display = "block";
			if (i > 0) {
				el.style.borderTop = "1px solid black";
			}
			el.classList.add("adm_file_wrapper");

			var icon = nf["isDir"] ? "folder" : nf["name"].split("/").reverse()[0].split(".").reverse()[0];
			var iconURI = "https://rion.cz/icons/" + self.getIcoForExtension(icon) + ".png";

			var ico = document.createElement("img");
			ico.src = iconURI;

			el.appendChild(ico);

			var els = document.createElement("span");
			els.innerHTML = newFiles[i]["name"];
			el.appendChild(els);

			el.addEventListener("dblclick", function() {
				self.navigator.doubleClickOnFileOrFolder(nf);
			});

			if (mobileAndTabletCheck()) {
				el.addEventListener("click", function() {
					self.navigator.doubleClickOnFileOrFolder(nf);
				});
			}

			self.filesPnl.appendChild(el);
		}

		for (var i = 0; i < newFiles.length; i++) {
			constr(newFiles[i]);
		}
	}

	self.openUI = function() {
		if(self.originalUIVisibilities === undefined) {
			self.originalUIVisibilities = []
			for(var i = 0; i < document.body.childElementCount; i++) {
				var child = document.body.children[i];
				self.originalUIVisibilities.push([child, child.style.display]);
				child.style.display = "none";
			}
		}
		/*
		self.originalUIVisibilities["txtHeader"] = txtHeader.style.display;
		self.originalUIVisibilities["pnlWarnID"] = pnlWarnID.style.display;
		self.originalUIVisibilities["id_indiv"] = id_indiv.style.display;
		if(document.getElementById("id_stats")) {
		    self.originalUIVisibilities["id_stats"] = id_stats.style.display;
			id_stats.style.display = "none";
		}
		if(document.getElementById("id_faq")) {
			self.originalUIVisibilities["id_faq"] = id_faq.style.display;
			id_faq.style.display = "none";
		}
		txtHeader.style.display = "none";
		pnlWarnID.style.display = "none";
		id_indiv.style.display = "none";
		*/
		
		self.mainUI.style.display = "";
		self.editor.hideEditors();
		self.editor.hideButtons();
		self.editor.setFileNameLabel("");
		
	}

	self.closeUI = function() {
		if(self.originalUIVisibilities.length) {
			for(var i = 0; i < self.originalUIVisibilities.length; i++) {
				var item = self.originalUIVisibilities[i];
				var child = item[0];
				var original = item[1];
				child.style.display = original;
			}
		}
		self.originalUIVisibilities = undefined;
		/*
		txtHeader.style.display = self.originalUIVisibilities["txtHeader"];
		pnlWarnID.style.display = self.originalUIVisibilities["pnlWarnID"];
		id_indiv.style.display = self.originalUIVisibilities["id_indiv"];
		if("id_stats" in self.originalUIVisibilities) {
			id_stats.style.display = self.originalUIVisibilities["id_stats"];
		}
		if("id_faq" in self.originalUIVisibilities) {
			id_faq.style.display = self.originalUIVisibilities["id_faq"];
		}
		*/
		self.mainUI.style.display = "none";
	}

	self.loadFile = function(fl) {
		self.async("load", {"fileID": fl["ID"]}, function(data) {
			self.editor.editFile(data);
		}, function(err) {
			self.common.showError("Chyba", "Nepodařilo se kontaktovat server", true, err);
		}, true)
	}

	self.async = function(action, data, cbOk, cbFail, parseResult) {
		self.setWaiterVisible(true);
		var asyncData = {
			"action" : "ADMIN",
			"admin_data" : data
		};
		asyncData.admin_data.action = action
		self.common.async(asyncData, function(obj) {
			self.setWaiterVisible(false);
			cbOk(obj);
		}, function(err) {
			self.setWaiterVisible(false);
			cbFail(err);
		}, parseResult);
	}

	self.openAdmin = function() {
		self.openUI();
	}

	self.insertAdminButton = function() {
		if (self.common.addButtonToLoginPanel) {
			self.admButton = self.common.addButtonToLoginPanel("Administrace", function(){self.openAdmin();});
		} else if(btnLogout) {
			var bl = btnLogout;
			self.admButton = document.createElement("button");
			self.admButton.innerHTML = "Administrace";
			self.admButton.addEventListener("click", self.openAdmin);
			
			txtHeader.removeChild(bl);
			txtHeader.appendChild(self.admButton);
			txtHeader.appendChild(bl);
		}
		self.admButton.style.marginRight = "4px";
	}

	self.materialize = function() {
		self.insertAdminButton();
		self.createUI();
	}

	self.init = function() {
	 	self.editor = new AdminEditor(self);
		self.navigator = new AdminNavigator(self);
		self.materialize();
		self.editor.initEditor();
		self.closeUI();
	}

	self.init();
	return this;
};

var AdminNavigator = function(adminer) {
	var self = this;
	self.common = adminer.common;

	self.files = [];

	self.putFiles = function(files) {
		self.files = [];
		for (var i = 0; i < files.length; i++) {
			self.files.push(files[i]);
		}
		adminer.navigatorUpdate();
	}

	self.getFilesInCurrentDirectory = function() {
		var res = [];
		if (self.currentWorkingDir != "") {
			res.push({
				"name" : "..",
				"isDir" : true
			});
		}

		var reOr = function(file, data) {
			if (!data) {
				data = {};
			}
			var parts = file.split("/");
			if (parts.length == 1) { // In current dir
				var fname = parts[0];
				if (!(fname in data)) {

				}
			} else { // More nested dir

			}

			return data;
		}

		var cpParts = self.currentWorkingDir.split("/");

		var dirs = {};

		for (var fileI = 0; fileI < self.files.length; fileI++) {
			var fn = self.files[fileI];

			if (self.currentWorkingDir == "") { // Get all local files
				var parts = fn.name.split("/");
				var isRootFile = fn.name.replace("/", "").length == fn.name.length;
				var isInDirs = (parts[0] in dirs);
				if (isRootFile && !isInDirs) {
					dirs[fn] = true;
					res.push({
						"name" : fn.name,
						"isDir" : false,
						"obj" : fn
					});
				} else if (!isRootFile && !isInDirs) {
					dirs[parts[0]] = true;
					res.push({
						"name" : parts[0],
						"isDir" : true
					});
				}

			} else { // Get nested files
				if (fn.name.length > self.currentWorkingDir.length) {
					if (fn.name.substr(0, self.currentWorkingDir.length + 1) == (self.currentWorkingDir + "/")) { // Prefix match
						var nm = fn.name.substr(self.currentWorkingDir.length + 1);
						var parts = nm.split("/");
						var isRootFile = nm.replace("/", "").length == nm.length;
						var isInDirs = (parts[0] in dirs);
						if (isRootFile && !isInDirs) {
							dirs[fn] = true;
							res.push({
								"name" : nm,
								"isDir" : false,
								"obj" : fn
							});
						} else if (!isRootFile && !isInDirs) {
							dirs[parts[0]] = true;
							res.push({
								"name" : parts[0],
								"isDir" : true
							});
						}

					}
				}
			}
		}

		res.sort(function(a, b) {
			if ((a["isDir"] && b["isDir"]) || (!a["isDir"] && !b["isDir"])) {
				return ("" + a.name).localeCompare(b.name + "");
			} else if (a["isDir"]) {
				return -1;
			}
			return 1;

		})

		return res;
	}

	self.currentWorkingDir = "";

	self.doubleClickOnFileOrFolder = function(obj) {
		if (obj["isDir"]) {
			if (obj["name"] == "..") {
				var parts = self.currentWorkingDir.split("/");
				parts = parts.splice(0, parts.length - 1);
				self.currentWorkingDir = parts.join("/");
			} else {
				if (self.currentWorkingDir == "") {
					self.currentWorkingDir = obj["name"];
				} else {
					self.currentWorkingDir += "/" + obj["name"];
				}
			}
			adminer.navigatorUpdate();
		} else if (obj["obj"]) { // Opening file
			var fobj = obj["obj"];
			adminer.loadFile(fobj);
		}
	}

	self.reloadFiles = function() {
		var cbOK = function(data) {
			self.currentWorkingDir = "";
			self.putFiles(data);
		};
		var cbFail = function(err) {
			self.common.showError("Chyba", "Nepodařilo se kontaktovat server", true, err);
		};
		adminer.async("getFiles", {}, cbOK, cbFail);
	}

	return self;
};

var AdminEditor = function(adminer) {
	var self = this;
	self.common = adminer.common;

	self.initEditor = function() {
		self.editTable = adminer.editTable;
		self.editArea = adminer.editArea;

		self.btnReturn = adminer.btnReturn;
		self.btnEditView = adminer.btnEditView;
		self.btnClose = adminer.btnClose;
		self.btnSave = adminer.btnSave;
		self.btnSaveClose = adminer.btnSaveClose;

		self.pnlDetails = adminer.pnlDetails;

		self.fileLbl = adminer.fileLbl;

		self.btnClose.addEventListener("click", self.close);
		self.btnReturn.addEventListener("click", self.onReturnPress);
		self.btnSave.addEventListener("click", self.onSavePress);
		self.btnSaveClose.addEventListener("click", self.onSaveClose);
		self.btnEditView.addEventListener("click", self.onEditPress);

		self.hideEditors();
		self.hideButtons();
	}

	self.currentFilterObjs = {};

	self.resetFilters = function() {
		for ( var filterName in self.currentFilterObjs) {
			if (self.currentFilterObjs.hasOwnProperty(filterName)) {
				var filterObj = self.currentFilterObjs[filterName];
				filterObj.setter("");
			}
		}
		this.refilter();
	}

	self.refilter = function() {
		var filterData = {};

		var mapComp = function(filterName) {
			var filterObj = self.currentFilterObjs[filterName];
			var value = filterObj.getter();
			var type = filterObj.type;
			var comparator = function(valueF) {
				return valueF == value;
			};
			if (value.length > 0) {
				if (type == "TEXT" || type == "BIGTEXT" || type == "DATE" || type == "LOGIN") {
					comparator = function(filteredValue) {
						return filteredValue.toLowerCase().indexOf(value.toLowerCase()) >= 0
					};
				} else if (type == "INT") {
					if (value.substr(0, 2) == ">=") {
						var fltVal = value.substr(2) * 1;
						comparator = function(filteredValue) {
							return filteredValue * 1 >= fltVal
						};
					} else if (value.substr(0, 2) == "<=") {
						var fltVal = value.substr(2) * 1;
						comparator = function(filteredValue) {
							return filteredValue * 1 <= fltVal
						};
					} else if (value.substr(0, 1) == "<") {
						var fltVal = value.substr(1) * 1;
						comparator = function(filteredValue) {
							return filteredValue * 1 < fltVal
						};
					} else if (value.substr(0, 1) == ">") {
						var fltVal = value.substr(1) * 1;
						comparator = function(filteredValue) {
							return filteredValue * 1 > fltVal
						};
					}
				}
				filterData[filterName] = comparator;
			}
		}

		for ( var filterName in self.currentFilterObjs) {
			if (self.currentFilterObjs.hasOwnProperty(filterName)) {
				mapComp(filterName);
			}
		}
		for (var dataI = 0; dataI < self.currentlyEditing.shown.length; dataI++) {
			var dataObj = self.currentlyEditing.shown[dataI];
			var passedFilters = true;
			for ( var filterName in filterData) {
				if (filterData.hasOwnProperty(filterName)) {
					var filterValue = filterData[filterName];
					if (dataObj.cols[filterName]) {
						var cell = dataObj.cols[filterName];
						var value = cell.innerHTML;
						if (!filterValue(value)) {
							passedFilters = false;
						}
					}
				}

			}
			if (!passedFilters) {
				dataObj.rowObj.style.display = "none";
			} else {
				dataObj.rowObj.style.display = "table-row";
			}
		}
	}

	self.hideButtons = function() {
		self.btnReturn.style.display = "none";
		self.btnEditView.style.display = "none";
		self.btnClose.style.display = "none";
		self.btnSave.style.display = "none";
		self.btnSaveClose.style.display = "none";
	}

	self.onReturnPress = function() {
		self.hideEditors();
		self.editTable.style.display = "table";
		var ext = self.currentlyEditing.name.split(".").reverse()[0];
		if (ext == "view") {
			self.showViewTableButtons();
		} else if (ext == "table") {
			self.showTableTableButtons();
		}
	}

	self.onEditPress = function() {
		if (self.btnEditView.innerHTML == "Edit") { // On tabular view -> show editor
			self.hideEditors();
			self.showViewEditButtons();
			self.show
			self.editArea.style.display = "block";
			self.editArea.value = JSON.parse(self.currentlyEditing.contents).SQL;
		} else { // In editor -> show tabular
			self.onReturnPress();
		}
	}

	self.onSaveClose = function() {
		self.onSavePress(true);
	}

	self.onSavePress = function(close) {
		var ext = self.currentlyEditing.name.split(".").reverse()[0];
		var isInViewEdit = self.btnEditView.innerHTML == "View";
		if (ext == "view") {
			if (isInViewEdit) { // Editing SQL
				var changed = self.editArea.value;
				adminer.async("save", {"fileID": self.currentlyEditing.ID, "contents" : changed}, function(data) {
					if (close === true) {
						self.close();
					}
				}, function(err) {
					self.common.showError("Chyba", "Nepodařilo se kontaktovat server", true, err);
				}, false);
			} else { // Details? Not allowed
				self.common.showError("Chyba", "Nepovolená operace", true);
			}
		} else if (ext == "table") {
			// Collect currently editing fields
			var items = {};
			for (var editorI = 0; editorI < self.currentEditor.length; editorI++) {
				var editor = self.currentEditor[editorI];
				var name = editor.name;
				var value = editor.getter();
				items[name] = value;
			}
			var rs = JSON.stringify(items);
			adminer.async("tableEdit", {"fileID": self.currentlyEditing.ID, "contents": rs}, function(data) {
				if (close === true) {
					self.close();
				}
			}, function(err) {
				self.common.showError("Chyba", "Nepodařilo se kontaktovat server", true, err);
			}, false)
		} else {
			var changed = self.editArea.value;
			adminer.async("save", {"fileID": self.currentlyEditing.ID, "contents": changed}, function(data) {
				if (close === true) {
					self.close();
				}
			}, function(err) {
				self.common.showError("Chyba", "Nepodařilo se kontaktovat server", true, err);
			}, false)
		}
	}

	self.setFileNameLabel = function(fileName) {
		self.fileLbl.innerHTML = fileName;
	}

	self.showCloseButton = function() {
		self.btnClose.style.display = "inline-block";
	}

	self.showCloseAndSaveButton = function() {
		self.btnSaveClose.style.display = "inline-block";
	}

	self.showReturnButton = function() {
		self.btnReturn.style.display = "inline-block";
	}

	self.showEditButton = function() {
		self.btnEditView.style.display = "inline-block";
		self.btnEditView.innerHTML = "Edit";
	}

	self.showSaveButton = function() {
		self.btnSave.style.display = "inline-block";
	}

	self.showViewButton = function() {
		self.btnEditView.style.display = "inline-block";
		self.btnEditView.innerHTML = "View";
	}

	self.showTableTableButtons = function() {
		self.hideButtons();
		self.showCloseButton();
	}

	self.showTableDetailsButtons = function() {
		self.hideButtons();
		self.showCloseButton();
		self.showSaveButton();
		self.showCloseAndSaveButton();
		self.showReturnButton();
	}

	self.showEditFileRawButtons = function() {
		self.hideButtons();
		self.showCloseButton();
		self.showSaveButton();
		self.showCloseAndSaveButton();
	}

	self.showEditRawFile = function(contents) {
		self.hideEditors();
		self.editArea.style.display = "block";
		self.editArea.value = contents;
	}

	self.extractColumns = function(contents) {
		const regex = /(LOGIN|TEXT|BIGTEXT|INT|DATE)\((\w+)\)/gm;
		var sql = contents.SQL;
		var cols = [];
		var match = regex.exec(sql);
		while (match != null) {
			var colType = match[1];
			var colName = match[2];
			cols.push({
				"name" : colName,
				"type" : colType,
				"index" : colName
			});
			match = regex.exec(sql);
		}
		return cols;
	}

	self.getTimeFromTimestamp = function(timestamp) {

		var lz = function(n) {
			return (n <= 9) ? "0" + n : n
		}

		var d = new Date(timestamp * 1);
		return lz(d.getDate()) + ". " + lz(d.getMonth() + 1) + ". " + d.getFullYear() + " " + lz(d.getHours()) + ":" + lz(d.getMinutes());
	}

	self.parseDate = function(dateStr) {
		var i1 = dateStr.indexOf(".");
		if (i1 > 0) {
			var d = dateStr.substr(0, i1).trim() * 1;
			dateStr = dateStr.substr(i1 + 1);
			var i2 = dateStr.indexOf(".");
			if (i2 > 0) {
				var m = (dateStr.substr(0, i2).trim() * 1) - 1;
				dateStr = dateStr.substr(i2 + 1).trim();
				var i3 = dateStr.indexOf(" ");
				if (i3 > 0) {
					var y = (dateStr.substr(0, i3).trim() * 1);
					dateStr = dateStr.substr(i3 + 1).trim();
					var i4 = dateStr.indexOf(":");
					if (i4 > 0) {
						var h = (dateStr.substr(0, i4).trim() * 1);
						var min = dateStr.substr(i4 + 1).trim();
						var date = new Date();
						date.setDate(d);
						date.setMonth(m);
						date.setYear(y);
						date.setHours(h);
						date.setMinutes(min);
						date.setSeconds(0);
						date.setMilliseconds(0);
						return date.getTime();
					}
				}
			}
		}
		return false;
	}

	self.showDetails = function(row) {
		self.hideEditors();

		self.pnlDetails.style.display = "block";
		self.pnlDetails.innerHTML = "";

		var table = document.createElement("table");

		var readOnly = self.currentlyEditing.name.split(".").reverse()[0] != "table";

		self.currentEditor = [];

		var rowMat = function(col) {
			var tr = document.createElement("tr");
			var th = document.createElement("th");
			var td = document.createElement("td");

			th.innerHTML = col.name + ": ";
			var value = col.value;
			var getter;

			if (col.type == "BIGTEXT") {
				var el = document.createElement("textarea");
				el.innerHTML = value;
				td.appendChild(el);
				if (readOnly || col.name == "ID") {
					el.readOnly = true;
				}
				getter = function() {
					return el.value;
				};
			} else {
				var el = document.createElement("input");
				if (col.type == "DATE") {
					var originalValue = value;
					getter = function() {
						var a = self.parseDate(el.value);
						if (a === false) {
							return originalValue;
						} else {
							return a;
						}
					};
					value = self.getTimeFromTimestamp(value);
				} else {
					getter = function() {
						if (col.type == "INT") {
							return el.value * 1;
						} else {
							return el.value;
						}
					};
				}
				el.type = "text";
				el.value = value;
				td.appendChild(el);
				if (readOnly || col.name == "ID") {
					el.readOnly = true;
				}
			}
			self.currentEditor.push({
				"name" : col.name,
				"getter" : getter
			});

			tr.appendChild(th);
			tr.appendChild(td);
			table.appendChild(tr);
		}

		for (var colI = 0; colI < row.length; colI++) {
			var col = row[colI];
			rowMat(col);
		}

		self.pnlDetails.appendChild(table);

		var ext = self.currentlyEditing.name.split(".").reverse()[0];
		if (ext == "table") {
			self.showTableDetailsButtons();
		} else if (ext == "view") {
			self.showViewDetailsButtons();
		}
	}

	self.close = function() {
		self.hideEditors();
		self.hideButtons();
		self.setFileNameLabel("");
	}

	self.hideEditors = function() {
		self.editTable.style.display = "none";
		self.editArea.style.display = "none";
		self.pnlDetails.style.display = "none";
	}

	self.showViewTableButtons = function() {
		self.hideButtons();
		self.showCloseButton();
		self.showEditButton();
	}

	self.showViewDetailsButtons = function() {
		self.hideButtons();
		self.showCloseButton();
		self.showReturnButton();
	}

	self.showViewEditButtons = function() {
		self.hideButtons();
		self.showCloseButton();
		self.showViewButton();
		self.showSaveButton();
		self.showCloseAndSaveButton();
	}

	self.materializeTable = function(columns, data) {
		self.currentData = data;
		self.currentColumns = columns;
		self.editTable.innerHTML = "";
		self.currentlyEditing.shown = [];
		self.currentFilterObjs = {};

		var filterRow = document.createElement("tr");
		var headerRow = document.createElement("tr");

		var matFilter = function() {
			var filterCell = document.createElement("th");
			var filterEl = document.createElement("input");
			filterEl.type = "text";
			self.currentFilterObjs[columns[i].name] = {
				"getter" : function() {
					return filterEl.value;
				},
				"setter" : function(value) {
					filterEl.value = value;
				},
				"type" : columns[i].type
			};
			filterEl.classList.add("adm_content_table_filter");
			var changeCB = function() {
				self.refilter();
			};
			filterEl.addEventListener("change", changeCB);
			filterEl.addEventListener("keyup", changeCB);

			filterCell.appendChild(filterEl);
			filterRow.appendChild(filterCell);
		}

		for (var i = 0; i < columns.length; i++) {
			matFilter(i);
			var headerCell = document.createElement("th");
			headerCell.innerHTML = columns[i].name;
			headerRow.appendChild(headerCell);

		}

		self.editTable.appendChild(filterRow);
		self.editTable.appendChild(headerRow);

		var materializeRow = function(rowObj) {
			var row = document.createElement("tr");

			var dataObj = {};
			dataObj.rowObj = row;
			dataObj.cols = {};

			for (var colI = 0; colI < columns.length; colI++) {
				var colName = columns[colI].name;
				var colType = columns[colI].type;
				var colIndex = columns[colI].index;
				var cellObj = rowObj[colIndex];
				if (cellObj && (colType == "TEXT" || colType == "BIGTEXT" || colType == "LOGIN")) {
					cellObj = cellObj.split("<").join("&lt;").split(">").join("&gt;");
				} else if (cellObj && colType == "DATE") {
					cellObj = self.getTimeFromTimestamp(cellObj);
				}
				var cell = document.createElement("td");

				dataObj.cols[colName] = cell;

				if (colType == "BIGTEXT") {
					if (!cellObj) {
						cellObj = "";
					}
					var sz = cellObj.length;
					var unit = "bajtů";
					if(sz < 5 * 1024) {
					   // nothing
					} else {
					  sz = Math.round((sz*100) / 1024)/100;
					  unit = "KB";
					}
					cellObj = "<i>" + sz + " "+unit+"</i>";
				}
				cell.innerHTML = cellObj
				row.appendChild(cell);
			}

			self.currentlyEditing.shown.push(dataObj);

			self.editTable.appendChild(row);

			var editCB = function() {
				// Construct object from columsn
				var robj = [];
				for (var colI = 0; colI < columns.length; colI++) {
					var colName = columns[colI].name;
					var colType = columns[colI].type;
					var colIndex = columns[colI].index;
					robj.push({
						"name" : colName,
						"type" : colType,
						"value" : rowObj[colIndex]
					});
				}
				self.showDetails(robj);
			}

			if (mobileAndTabletCheck()) {
				row.addEventListener("click", editCB);
			}

			row.addEventListener("dblclick", editCB);
		}

		if(data && data.length) {
			for (var rowI = 0; rowI < data.length; rowI++) {
				var rowObj = data[rowI];
				materializeRow(rowObj);
			}
		}
	}

	self.showViewEdit = function(contents) {

	}

	self.showViewTable = function(contents) {
		var c = JSON.parse(contents);
		var cols = self.extractColumns(c);

		self.hideEditors();
		self.editTable.style.display = "table";

		self.materializeTable(cols, c.result);
	}

	self.showTableView = function(contents) {
		var c = JSON.parse(contents);
		var columns = [];
		var colTypes = [ "INT", "TEXT", "BIGTEXT", "DATE" ];
		if(c && c.columns && c.columns.length) {
			for (var colI = 0; colI < c.columns.length; colI++) {
				var colParts = c.columns[colI].substr(1).split("@");
				var colType = c.columns[colI].substr(0, 1) * 1;
				var colName = colParts[1];
				columns.push({
					"name" : colName,
					"type" : colTypes[colType],
					"index" : colI
				});
			}
		}
		self.hideEditors();
		self.editTable.style.display = "table";

		self.materializeTable(columns, c.data);
	}

	self.editFile = function(fn) {
		self.hideEditors();
		self.hideButtons();
		self.setFileNameLabel("");
		self.currentlyEditing = fn;
		var fo = fn["fo"];
		if (fo == 1) {
			self.common.showError("Chyba", "Zadaný soubor neexistuje", true);
			return;
		}
		var name = fn["name"];
		var id = fn["ID"];
		var contents = fn["contents"];
		var ext = name.split(".").reverse()[0];
		if (ext == "table") {
			self.showTableTableButtons();
			self.showTableView(contents);
		} else if (ext == "view") {
			self.showViewTableButtons();
			self.showViewTable(contents);
		} else {
			self.showEditFileRawButtons();
			self.showEditRawFile(contents);
		}
		self.setFileNameLabel(name);
	}

	return self;
};

window.pastAload = function() {
	var admin = new Admin();
}
