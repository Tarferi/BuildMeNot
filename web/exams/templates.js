
var UITemplates = function() {
	var self = this;

	self.HeaderUI = {
	  "type": "table",
	  "id": "tbl1",
	  "contents": [
	    {
	      "type": "thead",
	      "contents": [
	        {
	          "type": "tr",
	          "contents": [
	            {
	              "type": "th",
	              "id": "pnl_ex_name",
	              "innerHTML": "Zkouška",
	              "class": "hheader",
				  "colSpan": 4
	            }
	          ]
	        },
	        {
	          "type": "tr",
	          "contents": [
	            {
	              "type": "th",
	              "innerHTML": "Zkoušený",
	              "class": "hcol1"
	            },
	            {
	              "type": "td",
	              "id": "pnl_name",
	              "innerHTML": "Karel",
	              "class": "hcol2",
				   "colSpan": 3
	            }
	          ]
	        },
	        {
	          "type": "tr",
	          "contents": [
	            {
	              "type": "th",
	              "innerHTML": "Počet otázek",
	              "class": "hcol1"
	            },
	            {
	              "type": "td",
	              "id": "pnl_total_questions",
	              "innerHTML": "5",
	              "class": "hcol2"
	            },
	            {
	              "type": "th",
	              "innerHTML": "Maximální bodový zisk",
	              "class": "hcol1"
	            },
	            {
	              "type": "td",
	              "id": "pnl_total_points",
	              "innerHTML": "10 bodů",
	              "class": ["hcol2","hcolR"]
	            }
	          ]
	        },
	        {
	          "type": "tr",
	          "contents": [
	            {
	              "type": "th",
	              "innerHTML": "Datum",
	              "class": "hcol1"
	            },
	            {
	              "type": "td",
	              "id": "pnl_date_now",
	              "innerHTML": "",
	              "class": "hcol2"
	            },
	            {
	              "type": "th",
	              "innerHTML": "Čas",
	              "class": "hcol1"
	            },
	            {
	              "type": "td",
	              "id": "pnl_time_now",
	              "innerHTML": "",
	              "class": "hcol2"
	            }
	          ]
	        },
	        {
	          "type": "tr",
	          "contents": [
	            {
	              "type": "th",
	              "innerHTML": "Čas na vyplnění",
	              "class": "hcol1"
	            },
	            {
	              "type": "td",
	              "id": "pnl_time_left",
	              "innerHTML": "1:00:00",
	              "class": "hcol2",
				  "colSpan": 3
	            }
	          ]
	        },
	        {
	          "type": "tr",
			  "id": "row_act",
	          "contents": [
	            {
	              "type": "th",
	              "innerHTML": "Akce",
	              "class": "hcol1"
	            },
	            {
	              "type": "td",
	              "contents": [
	                {
	                  "type": "button",
	                  "id": "btn_start",
	                  "innerHTML": "Spustit"
	                },
	                {
	                  "type": "button",
	                  "id": "btn_submit",
	                  "innerHTML": "Odevzdat"
	                },
	                {
	                  "type": "button",
	                  "id": "btn_back",
	                  "innerHTML": "Zpět"
	                }
	              ],
	              "class": "hcol2",
                  "colSpan": 3
	            }
	          ]
	        }
	      ]
	    }
	  ]
	};
	
	self.QuestionUI = {
	  "type": "table",
	  "id": "tbl2",
	  "contents": [
	    {
	      "type": "thead",
	      "contents": [
	        {
	          "type": "tr",
	          "contents": [
	            {
	              "type": "th",
                  "colSpan": 2,
	              "contents": [
	                {
	                  "type": "div",
	                  "id": "question_id",
	                  "innerHTML": "Otázka 1",
	                  "class": "pnl_question_id"
	                }
	              ],
	              "class": "hheader pnl_question"
	            },
				{
					"type": "th",
					"innerHTML": "5 bodů",
					"id": "qb_points",
					 "class": ["hheader", "leftBorder"]			
				}
	          ]
	        },
	        {
	          "type": "tr",
	          "contents": [
	            {
	              "type": "td",
	              "id": "question_contents",
	              "innerHTML": "text",
	              "class": "pnl_question",
                  "colSpan": 3
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
	          "contents": [
	            {
	              "type": "td",
	              "innerHTML": "Odpověď:",
	              "class": "pnl_response_col1",
                  "colSpan": 3
	            }
	          ]
	        },
	        {
	          "type": "tr",
	          "contents": [
	            {
	              "type": "td",
	              "class": "pnl_response_col1"
	            },
	            {
	              "type": "td",
				  "class": "pnl_response",
				  "id": "pnl_resp_cont",
				  "colSpan": 2
	            }
	          ]
	        }
	      ]
	    }
	  ]
	};

	this.getUI = function(root) {
		var obj = {}
		if (!root.tagName) {
			return {};
		}
		obj.type = root.tagName.toLowerCase();
		if (root.id) {
			obj.id = root.id;
		}
		if(root.colSpan) {
			obj.colSpan = root.colSpan
		}
		if(root.rowSpan) {
			obj.rowSpan = root.rowSpan
		}
		if (root.childElementCount > 0) {
			var contents = []
			for (var i = 0; i < root.childElementCount; i++) {
				contents.push(self.getUI(root.children[i]));
			}
			obj.contents = contents;
		} else if (root.innerHTML.trim() != "") {
			obj.innerHTML = root.innerHTML;
		}
		if (root.classList.length == 1) {
			obj.class = root.classList[0];
		} else if (root.classList.length > 1) {
			var cl = [];
			for (var i = 0; i < root.classList.length; i++) {
				cl.push(root.classList[i]);
			}
			obj.class = cl.join(" ");
		}
		return obj;
	}

	return this;
} 