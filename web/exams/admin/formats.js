var ExamsAdminFormats = function() {
	var selfFormats = this;
	var outFormats = new CommonFormats();
	
	selfFormats.init = function() {
		
	}

	selfFormats.Generic = function() {
		var self = this;
		
		self.parse = function(data, key, value) {
			var rd = key.split(".");
			if (rd.length == 1 && data) {
				data[rd[0]] = value;
			} else if (rd.length == 2 && data && data[rd[0]] !== undefined) {
				data[rd[0]][rd[1]] = value;
			} else if (rd.length == 3 && data && data[rd[0]] !== undefined && data[rd[0]][rd[1]] !== undefined) {
				data[rd[0]][rd[1]][rd[2]] = value;
			} else if (rd.length == 4 && data && data[rd[0]] !== undefined && data[rd[0]][rd[1]] !== undefined && data[rd[0]][rd[1]][rd[2]] !== undefined) {
				data[rd[0]][rd[1]][rd[2]][rd[3]] = value;
			} else if (rd.length == 5 && data && data[rd[0]] !== undefined && data[rd[0]][rd[1]] !== undefined && data[rd[0]][rd[1]][rd[2]] !== undefined && data[rd[0]][rd[1]][rd[2]][rd[3]] !== undefined) {
				data[rd[0]][rd[1]][rd[2]][rd[3]][rd[4]] = value;
			}
			return data;
		}
		
		self.format = function(data, key, def) {
			var rd = key.split(".");
			var rm = data;
			for (var i = 0; i < rd.length; i++) {
				var f = rd[i];
				if (rm) {
					if (f in rm) {
						rm = rm[f];
					} else {
						rm = undefined
					}
				}
			}
			if (rm === undefined) {
				rm = def;
			}
			return rm;
		}
		
		return self;
	}
	
	selfFormats.TextFmt = function() {
		var self = this;
		var internal = new selfFormats.Generic();
	
		self.format = function(data, key, value) {
			return internal.format(data, key, value);
		}
	
		self.getDefaultValue = self.format;
	
		self.parse = function(data, key, value) {
			var rd = key.split(".");
			if (rd.length == 1 && data) {
				data[rd[0]] = value;
			} else if (rd.length == 2 && data && data[rd[0]]) {
				data[rd[0]][rd[1]] = value;
			} else if (rd.length == 3 && data && data[rd[0]] && data[rd[0]][rd[1]]) {
				data[rd[0]][rd[1]][rd[2]] = value;
			} else if (rd.length == 4 && data && data[rd[0]] && data[rd[0]][rd[1]]
					&& data[rd[0]][rd[1]][rd[2]]) {
				data[rd[0]][rd[1]][rd[2]][rd[3]] = value;
			} else if (rd.length == 5 && data && data[rd[0]] && data[rd[0]][rd[1]]
					&& data[rd[0]][rd[1]][rd[2]]
					&& data[rd[0]][rd[1]][rd[2]][rd[3]]) {
				data[rd[0]][rd[1]][rd[2]][rd[3]][rd[4]] = value;
			}
			return data;
		}
	
		return this;
	}
	
	selfFormats.IntFmt = function() {
		var self = this;
		var internal = new selfFormats.TextFmt();
	
		self.format = function(data, key) {
			var value = internal.format(data, key);
			if (value !== undefined) {
				return value * 1;
			}
			return 0;
		}
	
		self.getDefaultValue = self.format;
	
		self.parse = function(data, key, value) {
			return internal.parse(data, key, value * 1);
		}
	
		return this;
	};
	
	selfFormats.IntEnumFmt = function(values) {
		var self = this;
		self.values = values;
		var internal = new selfFormats.IntFmt();
	
		self.format = function(data, key) {
			var value = internal.format(data, key);
			if (value in self.values) {
				return self.values[value];
			}
			return 0;
		}
	
		self.getDefaultValue = function(data, key) {
			return internal.format(data, key);
		}
	
		self.parse = function(data, key, value) {
			return internal.parse(data, key, value * 1);
		}
	
		return this;
	}
	
	selfFormats.BoolFmt = function() {
		var self = this;
		var internal = new selfFormats.Generic();
		
		self.format = function(data, key) {
			var value = internal.format(data, key);
			return value ? 1 : 0;
		}
			
		self.getDefaultValue = self.format;
	
		self.parse = function(data, key, value) {
			return internal.parse(data, key, value == 1);
		}
		
		return this;
	}
	
	
	selfFormats.DateFmt = function() {
		var self = this;
		var internal = new selfFormats.IntFmt();
	
		self.format = function(data, key) {
			var value = internal.format(data, key);
			var dd = function(x) {
				if (x <= 9) {
					return "0" + x
				} else {
					return "" + x;
				}
			}
	
			if (value !== undefined) {
				value = value * 1;
				var date = new Date(value);
				var parts = [];
				parts.push(dd(date.getDate()));
				parts.push(dd(date.getMonth() + 1));
				parts.push(dd(date.getFullYear()));
	
				var dmy = parts.join(". ");
				parts = [];
				parts.push(dd(date.getHours()));
				parts.push(dd(date.getMinutes()));
	
				var tm = parts.join(":");
				if (value == 0) {
					return "";
				}
				return dmy + " " + tm
			}
			return "";
		}
	
		self.getDefaultValue = self.format;
	
		self.parse = function(data, key, value) {
			if (value.trim() == "") {
				value = 0;
			} else {
				var parts = value.split(".", 3);
				var day = parts[0] * 1;
				var month = parts[1] * 1;
	
				parts = parts[2].trim().split(" ", 2);
				var year = parts[0] * 1;
	
				parts = parts[1].trim().split(":");
	
				var hours = parts[0] * 1;
				var minutes = parts[1] * 1;
	
				var d = new Date(year, month - 1, day);
				d.setHours(hours);
				d.setMinutes(minutes);
				value = d.getTime();
			}
			return internal.parse(data, key, value);
		}
	
		return this;
	}
	
	
	selfFormats.TimeFmt = function() {
		var self = this;
		var internal = new selfFormats.IntFmt();
	
		self.format = function(data, key) {
			var value = internal.format(data, key);
			var dd = function(x) {
				if (x <= 9) {
					return "0" + x
				} else {
					return "" + x;
				}
			}
	
			if (value !== undefined) {
				value = Math.floor((value * 1)/1000); // sec
				var s = value % 60;
				value = Math.floor((value)/60);
				var m = value % 60;
				value = Math.floor((value)/60);
				var h = value; 
				
				var parts = [];
				parts.push(dd(h));
				parts.push(dd(m));
				parts.push(dd(s));
	
				var tm = parts.join(":");
				return tm
			}
			return "";
		}
	
		self.getDefaultValue = self.format;
	
		self.parse = function(data, key, value) {
			if (value.trim() == "") {
				value = 0;
			} else {
				var parts = value.split(":", 3);
	
				var hours = parts[0] * 1;
				var minutes = parts[1] * 1;
				var seconds = parts[2] * 1;
	
				value = hours;
				value*=60;
				value+=minutes;
				value*=60;
				value+=seconds;
				value*=1000;
			}
			return internal.parse(data, key, value);
		}
	
		return this;
	}
	
	selfFormats.ListFmt = function(itemFmt) {
		var self = this;
		var internal = new selfFormats.TextFmt();
		self.common = new Common();
	
		self.escapeString = function(item) {
			return JSON.stringify(item);
		}
		
		self.mySplit = function(item) {
			return JSON.parse("["+item+"]");
		}
	
		self.format = function(data, key) {
			var value = internal.format(data, key);
			if (value !== undefined) {
				if (itemFmt) {
					var nv = [];
					for (var i = 0; i < value.length; i++) {
						nv.push(itemFmt.format({
							"x" : value[i]
						}, "x"));
					}
					value = nv;
				}
				value = value.map(self.escapeString);
				
				return value.join(", ");
			}
			return "";
		}
	
		self.getDefaultValue = self.format;
	
		self.parse = function(data, key, value) {
			if (value.trim() == "") {
				value = [];
			} else {
				var parts = self.mySplit(value);
				value = []
				for (var i = 0; i < parts.length; i++) {
					var part = parts[i];
					if(part.trim) {
						part = part.trim();
					}
					if (itemFmt) {
						part = itemFmt.parse({
							"x" : part
						}, "x", part)["x"];
					}
					value.push(part);
				}
			}
			return internal.parse(data, key, value);
		}
	
		return this;
	}

	
	this.init();
	return this;
}