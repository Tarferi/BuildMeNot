var UITemplates = function() {
	var self = this;
	self.UI = 
       {
          "type": "div",
          "class": "term_table_w0",
          "id": "term_table_bottom_spanner",
          "contents": [
          		{
          		   "type": "div",
          		   "class": "term_table_w00",
          		   "id": "nwBorder",
          		   "contents": [
          		   		{
          		   		   "type": "div",
          		   		   "class": "term_table_w001",
          		   		   "id": "txtBrief"
          		   		},
          		   		{
          		   		   "type": "div",
          		   		   "class": "term_table_w002",
          		   		   "contents": [
          		   		   		{
          		   		   		   "type": "button",
          		   		   		   "id": "btnRefresh",
          		   		   		   "innerHTML": "Aktualizovat"
          		   		   		},
          		   		   		{
          		   		   		   "type": "button",
          		   		   		   "id": "btnHide",
          		   		   		   "innerHTML": "Skrýt"
          		   		   		}
          		   		   ]
          		   		}
          		   
          		   ]
          		},
          		{
          			"type": "div",
          			"class": "term_table_w1",
          			"id": "pnlMain",
          			"contents": [
          				{
          					"type": "div",
          					"class": "term_table_w12",
          					"contents": [
          						{
              						"type": "div",
          							"class": "term_table_w121",
          							"innerHTML": "Popis"
          						}, {
          						 	"type": "div",
          						 	"class": "term_table_w122",
          						 	"id": "txtDescr"
          						}
          					]
          				},
          				{
          				   "type": "div",
          				   "class": "term_table_w13",
          				   "contents": [
								{
									"type": "div",
									"class": "term_table_w131",
									"innerHTML": "Přihlášení",
									"id": "lbl_SignTitle"
								},
								{
									"type": "div",
									"class": "term_table_w132",
									"id": "pre_adm_appender",
									"contents": [
										{
											"type": "table",
											"class": "term_table",
											"id": "term_loginTable",
											"contents": [
												{
													"type": "tr",
													"contents": [
														{
															"type": "th",
															"innerHTML": "Varianta",
															"id": "lbl_Variant"
														},
														{
															"type": "th",
															"innerHTML": "Kapacita",
															"id": "lbl_Capacity"
														},
														{
															"type": "th",
															"innerHTML": "Přihlášeno",
															"id": "lbl_SignedUp"
														},
														{
															"type": "th",
															"innerHTML": "Přihlášení",
															"id": "lbl_SigningUp"
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
	};
      
	self.admUITable = {
		"type": "table",
		"class": "term_table",
		"id": "term_adm",
		"contents": [
			{
				"type": "tr",
				"contents": [
					{
						"type": "th",
						"colSpan": 4,
						"id": "term_adm_var",
						"class": "term_adm_var_title",
						"innerHTML": ""
					}
				
				]
			},
			{
				"type": "tr",
				"class": "term_adm_var_title",
				"contents": [
					{
						"type": "th",
						"innerHTML": "#"
					},
					{
						"type": "th",
						"innerHTML": "Login"
					},
					{
						"type": "th",
						"innerHTML": "Jméno"
					},
					{
						"type": "th",
						"innerHTML": "Čas přihlášení"
					}
				]
			}
		]
	};
      
	self.admUI = {
		"type": "tr",
		"class": "term_adm_var_tr",
	 	"contents": [
			{
				"type": "td",
				"id": "cellOrder"
			},
								{
				"type": "td",
				"id": "cellLogin"
			},
			{
				"type": "td",
				"id": "cellName"
			},
			{
				"type": "td",
				"id": "cellTime"
			}        		
	  	]
	};   
       
    self.signUI = {
		"type": "tr",
  		"contents": [
			{
				"type": "td",
				"id": "cellName"
			},
								{
				"type": "td",
				"id": "cellCap"
			},
			{
				"type": "td",
				"id": "cellNow"
			},
			{
				"type": "td",
				"contents": [
					 {
					 	"type": "span",
					 	"class": "term_table_logged",
					 	"id": "term_table_logged"
					 },
					 {
					 	"type": "button",
					 	"id": "btnLog",
					 	"innerHTML": "Odhlásit"
					 }
				]
			}        		
  		]
	};
    
	return this;
}