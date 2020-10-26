var CommonFormats = function() {
	var self = this;
	
	self.supportedTags = ["math", "code"];
	
	var formatMath = function(text) {
		var data = {"throwOnError": false};
		var html = katex.renderToString(text, data);
		return html;
	};
	
	var formatCode = function(text) {
		var el = document.createElement("span");
		el.style.paddingLeft = "5px";
		el.style.paddingRight = "5px";
		el.style.marginTop = "5px";
		el.style.background = "#eeeeee";
		el.style.border = "1px solid #cccccc";
		el.style.fontFamily = "Courier, \"Lucida Console\", monospace";
		el.innerHTML = text;
		return el.outerHTML;
	}
	
	var formatByTag = function(tag, text) {
		if(tag == "math") {
			return formatMath(text);
		} else if(tag == "code") {
			return formatCode(text);
		}
		return text;
	}
	
	self.format = function(data) {
		var lastPos = 0;
		var indexes = [];
		data = data + "";
		for(var tagI = 0; tagI < self.supportedTags.length; tagI++) {
			var tag = self.supportedTags[tagI];
			var btag = "<" + tag + ">";
			var etag = "</" + tag + ">";
			while(true) {
				var pos = data.indexOf(btag, lastPos);
				if(pos == -1) {
					break;
				} else {
					var pos2 = data.indexOf(etag, pos);
					if(pos2 == -1) {
						break;
					} else {
						pos2 += etag.length;
						lastPos = pos2;
						indexes.push([pos, pos2, tag]);
					}
				}
			}
		}
		for(var i = indexes.length - 1; i >= 0; i--) {
			var posData = indexes[i];
			var begin = posData[0];
			var end = posData[1];
			var tag = posData[2];
			var btag = "<" + tag + ">";
			var etag = "</" + tag + ">";
			var length = (end - etag.length) - (begin + btag.length);
			
			var before = data.substr(0, begin);
			var after = data.substr(end);
			var contents = data.substr(begin + btag.length, length);
			
			var repl = formatByTag(tag, contents);
			
			data = before + repl + after;
		}
		
		return data;
	}

	self.init = function() {
		
	}


	self.init();
	return this;	
}