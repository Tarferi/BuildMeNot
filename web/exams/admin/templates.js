var ExamsAdminUITemplates = function() {
	var self = this;

	this.usersHeaderUI = {
		"type": "div",
		"contents": [
			{
				"type": "div",
				"innerHTML": "Správa Výsledků",
				"class": "pnl_title"
			},
			{
				"type": "table",
				"contents": [
					{
						"type": "thead",
						"contents": [
							{
								"type": "tr",
								"contents": [
									{
										"type": "th",
										"colSpan": 5,
										"contents": [
											{
												"type": "div",
												"contents": [
													{
														"type": "button",
														"id": "ex_back2",
														"innerHTML": "Zpět"
													},
													{
														"type": "button",
														"id": "ex_update2",
														"innerHTML": "Aktualizovat"
													}
												],
												"class": "tab_exams_toppnl"
											}	
										]	
									}
								]
							},
							{
								"type": "tr",
								"contents": [
									{
										"type": "th",
										"class": "align_center",
										"innerHTML": " # "
									},
									{
										"type": "th",
										"class": "align_center",
										"innerHTML": "Login"
									},
									{
										"type": "th",
										"class": "align_center",
										"innerHTML": "Stav"
									},
									{
										"type": "th",
										"class": "align_center",
										"innerHTML": "Detaily"
									},
									{
										"type": "th",
										"class": "align_center",
										"innerHTML": " Akce "
									}
								]
							}
						]
					},
					{
						"type": "tbody",
						"id": "pnl_body"
					}
				],
				"class": "borderedTab tab_questions"
			},
			{
				"type": "div",
				"contents": [
					{
						"type": "button",
						"id": "ex_back",
						"innerHTML": "Zpět"
					},
					{
						"type": "button",
						"id": "ex_update",
						"innerHTML": "Aktualizovat"
					}
				],
				"class": "tab_exams_bottompnl"
			}
		],
		"class": "control_pnl"
	};
	
	
	this.usersRowUI = {
		"type": "tr",
		"contents": [
			{
				"type": "td",
				"id": "pnl_id",
				"class": "align_center"
			},
			{
				"type": "td",
				"id": "pnl_login"
			},
			{
				"type": "td",
				"id": "pnl_status"
			},
			{
				"type": "td",
				"id": "pnl_details"
			},
			{
				"type": "td",
				"contents": [
					{
						"type": "button",
						"id": "btn_open",
						"innerHTML": "Otevřít"
					}
				],
				"class": "pnl_tab_act"
			}
		]
	};


	this.examQuestionUI = {
		"type": "div",
		"contents": [
			{
				"type": "div",
				"innerHTML": "Správa Otázek",
				"class": "pnl_title"
			},
			{
				"type": "table",
				"contents": [
					{
						"type": "thead",
						"contents": [
							{
								"type": "tr",
								"contents": [
									{
										"type": "th",
										"class": "align_center",
										"innerHTML": " # "
									},
									{
										"type": "th",
										"class": "align_center",
										"innerHTML": " Název otázky "
									},
									{
										"type": "th",
										"class": "align_center",
										"innerHTML": " Popis otázky "
									},
									{
										"type": "th",
										"class": "align_center",
										"innerHTML": " Nastavení "
									},
									{
										"type": "th",
										"class": "align_center",
										"innerHTML": " Akce "
									}
								]
							}
						]
					},
					{
						"type": "tbody",
						"id": "pnl_body"
					}
				],
				"class": "borderedTab tab_questions"
			},
			{
				"type": "div",
				"contents": [
					{
						"type": "button",
						"id": "ex_new",
						"innerHTML": "Nová otázka"
					}
				],
				"class": "tab_exams_bottompnl"
			}
		],
		"class": "control_pnl"
	};
	
	
	this.examQuestionRowUI = {
		"type": "tr",
		"contents": [
			{
				"type": "td",
				"id": "pnl_id",
				"class": "align_center"
			},
			{
				"type": "td",
				"contents": [
					{
						"type": "div",
						"id": "set_view_name",
					},
					{
						"type": "div",
						"id": "set_edit_name",
						"contents": [
							{
								"type": "input",
								"id": "set_edit_name_editor"
							}
						]
					}
				]
			},
			{
				"type": "td",
				"id": "ex_name",
				"contents": [
					{
						"type": "div",
						"id": "set_view_descr",
					},
					{
						"type": "div",
						"id": "set_edit_qlist",
						"contents": [
							{
								"type": "textarea",
								"id": "set_edit_descr_editor"
							}
						]
					}
				]
			},
			{
				"type": "td",
				"contents": [
					{
						"type": "table",
						"contents": [
							{
								"type": "tbody",
								"contents": [
									{
										"type": "tr",
										"contents": [
											{
												"type": "th",
												"innerHTML": "Typ otázky"
											},
											{
												"type": "td",
												"id": "set_edit_type"
											},
											{
												"type": "td",
												"contents": [
													{
														"type": "select",
														"id": "edit_type_editor"
													}
												]
											}
										]
									},
									{
										"type": "tr",
										"id": "row_edit_opt",
										"contents": [
											{
												"type": "th",
												"innerHTML": "Možnosti k výběru"
											},
											{
												"type": "td",
												"contents": [
													{
														"type": "div",
														"id": "set_edit_opt"														
													},
													{
														"type": "textarea",
														"id": "edit_opt_editor"
													}
												]
												
											}
										]
									},
									{
										"type": "tr",
										"id": "row_edit_opt2",
										"contents": [
											{
												"type": "th",
												"innerHTML": "Více správných odpovědí"
											},
											{
												"type": "td",
												"contents": [
													{
														"type": "checkbox",
														"readonly": true,
														"id": "set_edit_opt2"														
													},
													{
														"type": "checkbox",
														"id": "edit_opt2_editor"
													}
												]
												
											}
										]
									}
								]
							}
						],
						"class": "configTab"
					}
				]
			},
			{
				"type": "td",
				"contents": [
					{
						"type": "button",
						"id": "btn_edit",
						"innerHTML": "Upravit"
					},
					{
						"type": "button",
						"id": "btn_save",
						"innerHTML": "Uložit"
					},
					{
						"type": "button",
						"id": "btn_cancel",
						"innerHTML": "Storno"
					},
					{
						"type": "button",
						"id": "btn_del",
						"innerHTML": "Smazat"
					}
				],
				"class": "pnl_tab_act"
			}
		]
	};


	this.examGroupUI = {
		"type": "div",
		"contents": [
			{
				"type": "div",
				"innerHTML": "Správa Skupin Otázek",
				"class": "pnl_title"
			},
			{
				"type": "table",
				"contents": [
					{
						"type": "thead",
						"contents": [
							{
								"type": "tr",
								"contents": [
									{
										"type": "th",
										"innerHTML": " # "
									},
									{
										"type": "th",
										"innerHTML": " Název skupiny "
									},
									{
										"type": "th",
										"innerHTML": " Otázky ve skupině "
									},
									{
										"type": "th",
										"innerHTML": " Nastavení "
									},
									{
										"type": "th",
										"innerHTML": " Akce "
									}
								]
							}
						]
					},
					{
						"type": "tbody",
						"id": "pnl_body"
					}
				],
				"class": "borderedTab tab_questions"
			},
			{
				"type": "div",
				"contents": [
					{
						"type": "button",
						"id": "ex_new",
						"innerHTML": "Nová skupina"
					}
				],
				"class": "tab_exams_bottompnl"
			}
		],
		"class": "control_pnl"
	};

	this.examGroupRowUI = {
		"type": "tr",
		"contents": [
			{
				"type": "td",
				"id": "pnl_id",
				"class": "align_center"
			},
			{
				"type": "td",
				"contents": [
					{
						"type": "div",
						"id": "set_view_name",
						"innerHTML": " Test 0"
					},
					{
							"type": "input",
							"id": "set_edit_name_editor"
					}
				]
			},
			{
				"type": "td",
				"class": "align_center",
				"contents": [
					{
						"type": "div",
						"id": "set_view_qlist"
					},
					{
						"type": "input",
						"id": "set_edit_qlist_editor"
					}
				]
			},
			{
				"type": "td",
				"contents": [
					{
						"type": "table",
						"contents": [
							{
								"type": "tbody",
								"contents": [
									{
										"type": "tr",
										"contents": [
											{
												"type": "th",
												"innerHTML": "Ohodnocení skupiny"
											},
											{
												"type": "td",
												"contents": [
													{
														"type": "div",
														"id": "set_eval"
													},
													{										
														"type": "input",
														"id": "edit_eval"
													}
												]
											}
										]
									}
								]
							}
						],
						"class": "configTab"
					}
				]
			},
			{
				"type": "td",
				"contents": [
					{
						"type": "button",
						"id": "btn_edit",
						"innerHTML": "Upravit"
					},
					{
						"type": "button",
						"id": "btn_save",
						"innerHTML": "Uložit"
					},
					{
						"type": "button",
						"id": "btn_cancel",
						"innerHTML": "Storno"
					},
					{
						"type": "button",
						"id": "btn_del",
						"innerHTML": "Smazat"
					}
				],
				"class": "pnl_tab_act"
			}
		]
	};

	this.examManagerTableUI = {
		"type": "div",
		"contents": [
			{
				"type": "div",
				"innerHTML": "Správa termínů",
				"class": "pnl_title"
			},
			{
				"type": "table",
				"contents": [
					{
						"type": "thead",
						"contents": [
							{
								"type": "tr",
								"contents": [
									{
										"type": "th",
										"innerHTML": " # "
									},
									{
										"type": "th",
										"innerHTML": " Název termínu "
									},
									{
										"type": "th",
										"innerHTML": " Kategorie otázek "
									},
									{
										"type": "th",
										"innerHTML": " Nastavení "
									},
									{
										"type": "th",
										"innerHTML": " Akce "
									}
								]
							}
						]
					},
					{
						"type": "tbody",
						"id": "pnl_extable_tbody"
					}
				],
				"class": [
					"borderedTab",
					"tab_exams"
				]
			},
			{
				"type": "div",
				"contents": [
					{
						"type": "button",
						"id": "ex_new",
						"innerHTML": "Nový termín"
					}
				],
				"class": "tab_exams_bottompnl"
			}
		],
		"class": "control_pnl"
	};

	this.examManagerTableRowUI = {
		"type": "tr",
		"contents": [
			{
				"type": "td",
				"innerHTML": " 0 ",
				"id": "pnl_id"
			},
			{
				"type": "td",
				"contents": [
					{
						"type": "div",
						"id": "set_view_name",
						"innerHTML": " Test 0"
					},
					{
						"type": "input",
						"id": "set_edit_name_editor"
					}
				]
			},
			{
				"type": "td",
				"contents": [
					{
						"type": "div",
						"id": "set_view_groups",
						"innerHTML": " Test 0"
					},
					{
						"type": "input",
						"id": "set_edit_groups_editor"
					}
				]
			},
			{
				"type": "td",
				"contents": [
					{
						"type": "table",
						"contents": [
							{
								"type": "tbody",
								"contents": [
									{
										"type": "tr",
										"contents": [
											{
												"type": "th",
												"innerHTML": "Viditelné od"
											},
											{
												"type": "td",
												"contents": [
													{
														"type": "div",	
														"id": "set_view_see_since"
													},
													{
														"type": "input",
														"id": "see_since"
													}
												]
											}
										]
									},
									{
										"type": "tr",
										"contents": [
											{
												"type": "th",
												"innerHTML": "Viditelné do"
											},
											{
												"type": "td",
												"contents": [
													{
														"type": "div",	
														"id": "set_view_see_until"
													},
													{
														"type": "input",
														"id": "see_until"
													}
												]
											}
										]
									},
									{
										"type": "tr",
										"contents": [
											{
												"type": "th",
												"innerHTML": "Vyplnitelné od"
											},
											{
												"type": "td",
												"contents": [
													{
														"type": "div",	
														"id": "set_view_save_since"
													},
													{
														"type": "input",
														"id": "save_since"
													}
												]
											}
										]
									},
									{
										"type": "tr",
										"contents": [
											{
												"type": "th",
												"innerHTML": "Vyplnitelné do"
											},
											{
												"type": "td",
												"contents": [
													{
														"type": "div",	
														"id": "set_view_save_until"
													},
													{
														"type": "input",
														"id": "save_until"
													}
												]
											}
										]
									},
{
										"type": "tr",
										"contents": [
											{
												"type": "th",
												"innerHTML": "Čas na vyplnění"
											},
											{
												"type": "td",
												"contents": [
													{
														"type": "div",	
														"id": "set_view_ex_length"
													},
													{
														"type": "input",
														"id": "edit_ex_length"
													}
												]
											}
										]
									}
								]
							}
						],
						"class": "configTab"
					}
				],
				"class": "pnl_ex_config"
			},
			{
				"type": "td",
				"contents": [
					{
						"type": "button",
						"id": "btn_edit",
						"innerHTML": "Upravit"
					},
					{
						"type": "button",
						"id": "btn_save",
						"innerHTML": "Uložit"
					},
					{
						"type": "button",
						"id": "btn_cancel",
						"innerHTML": "Storno"
					},
					{
						"type": "button",
						"id": "btn_del",
						"innerHTML": "Smazat"
					},
					{
						"type": "button",
						"id": "ex_results",
						"innerHTML": "Výsledky"
					}
				],
				"class": "pnl_tab_act"
			}
		]
	}

	this.getUI = function(root) {
		var obj = {}
		if (!root.tagName) {
			return {};
		}
		obj.type = root.tagName.toLowerCase();
		if (root.id) {
			obj.id = root.id;
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