function ExamNoAccess() {
	alert("Nemáte dostatečná práva k přístupu k tomuto dokumentu");
}

$INJECT_CODE_NOPERMS(WEB.EXAMS.SEE, window.aload = ExamNoAccess;)$



var RadioRespose = function(data, permutation, readOnly) {
	var self = this;
	self.mapping = [];
	for(var i = 0; i < data.options.length; i++) {
		self.mapping.push[i];
	}
	if(permutation) {
		self.mapping = permutation.split("-").map(function(x){return x*1;});
	}
	
	self.revMapping = {};
	for(var i = 0; i < data.options.length; i++) {
		self.revMapping[self.mapping[i]] = i;
	}

	self.options = [];
	self.optionLabels = [];

	self.appendOption = function(optionID, optDescr, getLabelCB, setLabelCB, alignTop) {
		var optNode = self.createOption(optionID);
		self.options.push(optNode);
		self.optionLabels.push([optDescr, getLabelCB, setLabelCB]);
		self.node.appendChild(optNode);
		self.node.appendChild(optDescr);
		self.node.appendChild(document.createElement("br"));
		optNode.style.verticalAlign = "top";
	}
	
	self.createOption = function(index) {
		var type = data.multiple ? "checkbox" : "radio";
		var node = document.createElement("input");
		node.addEventListener("change", self.optChange);
		node.type = type;
		node.value = index;
		return node;		
	}
	
	self.setValue = function(value) {
		for(var i = 0; i < value.length; i++) {
			var v = value[i];
			var label = v.value;
			var myID = v.ID !== undefined ? self.mapping[v.ID] : self.mapping.length;
			self.options[myID].checked = true
			self.optionLabels[myID][2](self.optionLabels[myID][0], label);
		}
	}
	
	self.getValue = function() {
		var val = [];
		for(var i = 0; i < self.options.length; i++) {
			var realI = self.revMapping[i];
			var value = self.options[i].checked ? true : false;
			var label = self.optionLabels[i][1](self.optionLabels[i][0]);
			if(value) {
				if(realI !== undefined) {
					val.push({"ID": realI, "value": label});
				} else {
					val.push({"value": label});
				}
			}
		}
		return val;
	}
	
	self.optChange = function(ev) {
		var el = ev.target;
		if(readOnly) {
			return el.checked = !el.checked;
		} else {
			if(!data.multiple) {
				for(var i = 0; i < self.options.length; i++) {
					if(self.options[i] != el) {
						self.options[i].checked = false;
					}	
				}
			}
		}
	}
	
	self.materialize = function() {
		self.node = document.createElement("div");
		
		for(var i = 0; i < data.options.length; i++) {
			var opt = data.options[self.mapping[i]];
			var optDescr = document.createElement("span");
			optDescr.innerHTML = opt;
			var getLabelCB = function(labelObj) {return labelObj.innerHTML;};
			var setLabelCB = function(labelObj, label) {labelObj.innerHTML = label;};
			self.appendOption(self.mapping[i], optDescr, getLabelCB, setLabelCB);
		}
	}
	
	self.init = function() {
		self.materialize();
	}
	
	self.init();
	return this;
};

var RadioCustomRespose = function(data, initialContents, readOnly) {
	var self = this;
	var internal = new RadioRespose(data, initialContents, readOnly);
	
	self.materialize = function() {
		self.node = internal.node;
		self.customEditor = document.createElement("textarea");
		var getLabelCB = function(editor) {return editor.value;};
		var setLabelCB = function(editor, value) {editor.value = value;};
		self.customEditor.readOnly = readOnly;
		internal.appendOption(data.options.length, self.customEditor, getLabelCB, setLabelCB, true);
	}
	
	self.setValue = function(value) {
		return internal.setValue(value);
	}
	
	self.getValue = function() {
		var radioValue = internal.getValue();
		return radioValue;
	}
	
	self.init = function() {
		self.materialize();
	}
	
	self.init();
	return this;
};

var InputTextResponse = function(data, initialContents, readOnly) {
	var self = this;
	
	self.materialize = function() {
		self.node = document.createElement("input");
		self.node.type = "text";
		self.node.value = initialContents;
		self.node.readOnly = readOnly;
	}
	
	self.setValue = function(value) {
		return self.node.value = value;
	}
	
	self.getValue = function() {
		return self.node.value;
	}
	
	self.init = function(){
		self.materialize();
	}
	
	self.init();
	return this
}

var TextAreaResponse = function(data, initialContents, readOnly) {
	var self = this;
	
	self.materialize = function() {
		self.node = document.createElement("textarea");
		self.node.innerHTML = initialContents;
		self.node.readOnly = readOnly;
	}
	
	self.setValue = function(value) {
		return self.node.value = value;
	}
	
	self.getValue = function() {
		return self.node.value;
	}
	
	self.init = function(){
		self.materialize();
	}
	
	self.init();
	return this
};

var ExamQuestion = function(data, index, readOnly) {
	var self = this;
	self.ID = data.ID;
	
	
	self.common = new Common();
	self.templates = new UITemplates();
	
	var responseTemplates = [RadioRespose, RadioCustomRespose, InputTextResponse, TextAreaResponse];
	
	self.pointsFmt = function(bodu) {
		return bodu +" bod" + (bodu == 1 ? "" : bodu >= 2 && bodu <= 4 ? "y" : "ů");
	}
	
	self.setValue = function(values) {
		for(var i = 0; i < values.length; i++) {
			if(values[i].ID == self.ID) {
				self.responder.setValue(values[i].Value);
				return;
			}
		}
	}
	
	self.getValue = function() {
		return {"ID": self.ID, "Permutations": data.Permutations, "Value": self.responder.getValue()};
	}
	
	self.materialize = function() {
		var d = self.common.reconstructUI(self.templates.QuestionUI);
		self.node = d[0];
		
		var ids = d[1];
		
		ids.question_id.innerHTML = "Otázka " + (index + 1);
		ids.question_contents.innerHTML = data.Config.description;
		
		if(data.Config.type >= 0 && data.Config.type < responseTemplates.length) {
			self.responder = new responseTemplates[data.Config.type](data.Config, data.Permutations, readOnly);
			
			ids.pnl_resp_cont.appendChild(self.responder.node);
		}
		
		ids.qb_points.innerHTML = self.pointsFmt(data.Evaluation);
	}
	
	
	self.init = function() {
		self.materialize();	
	}
	
	self.init();
	return this;
}


var ExamMainHeader = function(data, startCB, turnCB, fromAdmin) {
	var self = this;
	
	if(fromAdmin) {
		data.ReadOnly = true;
	}
	
	self.data = data;
	self.templates = new UITemplates();
	self.common = new Common();
	
	self.node = null;
	
	self.examBeginTime = undefined;
	self.examLength = undefined
	
	self.tick = function() {
		var now  = self.common.getRemoteNow();
		
		if(self.pnlDate && self.pnlTime) {
			self.pnlDate.innerHTML = self.common.convertDate(now);
			self.pnlTime.innerHTML = self.common.convertOnlyTime(now); 
		}
		if(self.pnlCountdown && self.examBeginTime !== undefined) {
			if(now - self.examBeginTime > self.examLength) {
				self.btnTurn.style.display = "none";
			} else {
				self.pnlCountdown.innerHTML = self.common.convertTime(self.examLength - (now - self.examBeginTime));
			}
		}
		setTimeout(self.tick, 750);
	}
	
	self.pointsFmt = function(bodu) {
		return bodu +" bod" + (bodu == 1 ? "" : bodu >= 2 && bodu <= 4 ? "y" : "ů");
	}
	
	self.materialize = function() {
		var d = self.common.reconstructUI(self.templates.HeaderUI);
		self.node = d[0];
		var ids = d[1];
		
		ids.pnl_ex_name.innerHTML = data.ExamName;
		if(fromAdmin) {
			ids.pnl_name.innerHTML = fromAdmin.name;
		} else {
			ids.pnl_name.innerHTML = self.common.identity.name;
		}
		ids.pnl_total_questions.innerHTML = data.TotalQuestions;
		ids.pnl_time_left.innerHTML = self.common.convertTime(data.ExamTime)
		
		self.examLength = data.ExamTime
		
		self.btnStart = ids.btn_start;
		self.btnTurn = ids.btn_submit;
		
		self.pnlDate = ids.pnl_date_now;
		self.pnlTime = ids.pnl_time_now;
		self.pnlCountdown = ids.pnl_time_left;
		
		self.btnStart.addEventListener("click", function(){startCB(data.ExamID)});
		self.btnTurn.addEventListener("click", function(){turnCB(data.ExamID)});
		
		if(fromAdmin) {
			ids.btn_back.addEventListener("click", fromAdmin.close);
		} else {
			ids.btn_back.style.display = "none";
		}
		
		if(data.ReadOnly) {
			self.btnStart.style.display = "none";
			self.btnTurn.style.display = "none";
			self.pnlCountdown.innerHTML =self.common.convertDate(data.AnswerTime) + " "+  self.common.convertOnlyTime(data.AnswerTime);
			if(!fromAdmin) {
				ids.row_act.style.display = "none"
			}
		} else if (data.StartedAt !== undefined) {
			self.btnStart.style.display = "none";
			self.examBeginTime = data.StartedAt;
		} else {
			self.btnTurn.style.display = "none";
		}
		
		ids.pnl_total_points.innerHTML = self.pointsFmt(data.ExamTotalPoints);
		
		self.tick();
	}

		
	self.init = function() {
		self.materialize();
	}
	
	this.init();
	return self;
}

var ExamExamer = function(initLoad) {
	var self = this;
	self.common = new Common();
	self.root = document.createElement("div");
	self.root.classList.add("ex_root")
	
	self.questions = [];
	
	self.startExam = function(examID) {
		self.common.showLoader();
		var cbOK = function(data) {
			self.common.hideLoader();
			if(data.Available === true) {
				self.materialize(data);
			} else {
				self.common.showInitLoader("Nepodařilo se spustit termín", "red");
			}
		}
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showInitLoader("Nepodařilo se spustit termín:<br />" + err, "red");
		}
		var asyncData = {
			"action" : "HANDLE_EXAMS",
			"exam_data" : "begin_exam",
			"ID": examID
		};
		self.common.async(asyncData, cbOK, cbFail, true);	
	}
	
	self.turnExam = function(examID) {
		var resp = [];
		for(var i = 0; i < self.questions.length; i++) {
			var q = self.questions[i];
			resp.push(q.getValue());
		}	
		self.common.showLoader();
		var cbOK = function(data) {
			self.common.hideLoader();
			if(data.Available === true) {
				self.materialize(data);
			} else {
				self.common.showInitLoader("Nepodařilo se spustit termín", "red");
			}
		}
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showInitLoader("Nepodařilo se spustit termín:<br />" + err, "red");
		}
		var asyncData = {
			"action" : "HANDLE_EXAMS",
			"exam_data" : "finish_exam",
			"ID": examID,
			"Data": resp
		};
		self.common.async(asyncData, cbOK, cbFail, true);	
	}
	
	self.materialize = function(data, fromAdmin) {
		self.root.innerHTML = "";
		self.mainHeader = new ExamMainHeader(data, self.startExam, self.turnExam, fromAdmin);
		self.root.appendChild(self.mainHeader.node);
			
		if(data.Questions) {
			self.questions = [];
			for(var i = 0; i < data.Questions.length; i++) {
				var q = new ExamQuestion(data.Questions[i], i, data.ReadOnly, fromAdmin);
				self.questions.push(q);
				var br = document.createElement("br");
				self.root.appendChild(br);
				self.root.appendChild(q.node);
			}
			
			if(data.Answers) {
				for(var i = 0; i < self.questions.length; i++) {
					self.questions[i].setValue(data.Answers);
				}
			}
		}
	}

	self.loadExam = function(optGenID, fromAdmin) {
		var cbOK = function(data) {
			self.common.hideInitLoader();
			self.common.hideLoader();
			if(data.Available === true) {
				self.materialize(data, fromAdmin);
			} else {
				self.common.showInitLoader("Není k dispozici žádný termín", "red");
			}
		}
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showInitLoader("Nepodařilo se nahrát termín:<br />" + err, "red");
		}
		var asyncData = {
			"action" : "HANDLE_EXAMS",
			"exam_data" : "get_exam"
		};
		if(optGenID) {
			asyncData.exam_data = "get_result_by_id";
			asyncData.ID = optGenID;
		}
		self.common.async(asyncData, cbOK, cbFail, true);
	}
	
	self.init = function() {
		document.body.appendChild(self.root);
		if(initLoad!==false) {
			self.common.setLoginPanelVisible(false);
			self.common.showInitLoader("Nahrávám termín...", "grey");
			self.loadExam();
		}
	}
	
	self.init();
	return this;

}

function exam_load() {
	var ex = new ExamExamer(true);
	if(window.pastAload) {
		window.pastAload();
	}
}

$INJECT(exams/templates.js)$
$INJECT(common.js)$
$INJECT(WEB.ADMIN, admin.js)$