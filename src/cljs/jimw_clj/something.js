goog.provide("jimw_clj.something");

/* This file will be compiled by the ClojureScript compiler without
 * needing a foreign libs declaration in the build configuration. It's
 * position in the filesystem follows mirrors the its "namespace"
 * following the same conventions as ClojureScript.
 * Figwheel will hot reload this file as you save it.
*/

jimw_clj.something.hello = function() {return "Hey there from example.something JavaScript";}

jimw_clj.something.copyToClipboard = function (s) {
    document.addEventListener('copy', function(e){
        e.clipboardData.setData('text/plain', s);
        e.preventDefault();
    });
    document.execCommand('copy');
}

// <p class="p1">sam fff <a href="#">sam </a> te<span>samxx</span> dddn<span>sdsdddsamdsdds</span>eeds highlighting my text</p>
// 
// <p class="p2"> This is first appearance of word Hello This is second appearance of word Hello Yet another appearance of word Hello </p>
// 
// <input type="button" id="button" value="Highlight Selection" />

//var onClick = function() {
//    highlight("", "red");
//};

//$('#button').click(onClick);
//var selectionRange;

jimw_clj.something.highlight = function (highlightID, color, selectionRange) {
    if (window.getSelection && window.getSelection().toString()) {
        var node = jimw_clj.something.getSelectionParentElement(selectionRange);
        if (node != null) {
	    var text = getSelectionText();
	    console.log("Selected text: " + text);
	    jimw_clj.something.markFunc(node, text, color, selectionRange);
        } else {
            console.log("Parent nde is null for some reason");
        }
    } else {
  	console.log("tapped without selection");
    }
}

jimw_clj.something.getSelectionText = function () {
    if (window.getSelection) {
        var sel = window.getSelection();
        return sel.toString();
    }
};

jimw_clj.something.getSelectionParentElement = function (selectionRange) {
    var parentEl = null,
        sel;
    if (window.getSelection) {
        sel = window.getSelection();
        if (sel.rangeCount) {
            // selectionRange = sel.getRangeAt(0); 111111 
            parentEl = selectionRange.commonAncestorContainer;
            if (parentEl.nodeType != 1) {
                parentEl = parentEl.parentNode;
            }
        }
    } else if ((sel = document.selection) && sel.type != "Control") {
        parentEl = sel.createRange().parentElement();
    }
    return parentEl;
};

jimw_clj.something.markFunc = function (node, text, color, selectionRange) {
    console.log("https://markjs.io/....");
    var instance = new Mark(node);
    console.log("Mark instance :" + instance);
    instance.mark(text, {
        "element": "span",
	"className": color,
	"acrossElements": true,
	"separateWordSearch": false,
	"accuracy": "partially",
	"diacritics": true,
	"ignoreJoiners": true,
        "each": function(element) {
            element.setAttribute("id", "sohayb");
            element.setAttribute("title", "sohayb_title");
	},
        "done":function(totalMarks) {
            window.getSelection().empty();//This only in Chrome
            console.log("total marks: " + totalMarks);
        },
	"filter": function(node, term, totalCounter, counter) {
            var res = false;
            if (counter == 0) {
        	res = selectionRange.isPointInRange(node, selectionRange.startOffset);
            } else {
                res = selectionRange.isPointInRange(node, 1);
            }
            console.log("Counter: " + counter + ", startOffset: " + selectionRange.startOffset);
            return res;
	}
    });
};


// //////////////// makeSelection
// <div id="test">To be, or not to be: that is the question: Whether 'tis nobler in the mind to suffer the slings and arrows of outrageous fortune,</div>
// <button id="forward">Forward</button>
// <button id="backward">Backward</button>

//var textDiv = $("#test");
//var fBtn = $("#forward");
//var bBtn = $("#backward");

//fBtn.on("click", function() { makeSelection(10, 20); });
//bBtn.on("click", function() { makeSelection(10, 1); });

function makeSelection(a, b) {
    var textNode = textDiv[0].firstChild; // must get text node
    var selection = document.getSelection();
    selection.removeAllRanges();
    var range = document.createRange();
    // alert(textNode);
    range.setStart(textNode, a);
    range.setEnd(textNode, b);
    selection.addRange(range);    
}

console.log("This is printed at load time from example.something JavaScript");
