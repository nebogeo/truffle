// t r u f f l e Copyright (C) 2012 FoAM vzw   \_\ __     /\
//                                          /\    /_/    / /  
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as
// published by the Free Software Foundation, either version 3 of the
// License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

truffle.textbox=function(pos, text, w, h, font) {	
    truffle.drawable.call(this);

    this.original_text=text;
    this.its_a_tb=true;
    this.font = font;
    this.pos = pos;
    this.original_width=w;
    this.width = w;
    this.height = h;
    this.draw_bb = false;
    this.text_height = 20;
    this.text_colour="#000000";
    this.ready_to_draw=true;
    // add some to the bottom of bbox for lower part of letters like 'g'
    this.bottom_add=1.5;

    this.set_pos(pos);
    this.set_text(text);
}

truffle.textbox.prototype=inherits_from(truffle.drawable,truffle.textbox);

truffle.textbox.prototype.update_parent_tx=function(tx) {
    this.ready_to_draw=true;
    this.draw_me=true;
    this.parent_transform=tx;
}

truffle.textbox.prototype.wrap_text=function(ctx,phrase,maxPxLength) {
    var wa=phrase.split(" "),
    phraseArray=[],
    lastPhrase="",
    l=maxPxLength,
    measure=0;
    for (var i=0;i<wa.length;i++) {
        var w=wa[i];
        measure=ctx.measureText(lastPhrase+w).width;
        if (measure<l) {
            if (lastPhrase!="") {
                lastPhrase+=(" "+w);
            }
            else {
                lastPhrase+=w;
            }
        } else {
            if (lastPhrase!="") phraseArray.push(lastPhrase);
            lastPhrase=w;
        }
        if (i===wa.length-1) {
            if (lastPhrase!="") phraseArray.push(lastPhrase);
            break;
        }
    }
    return phraseArray;
}

truffle.textbox.prototype.set_text=function(text) {
    var that=this;
    var canvas=document.getElementById('canvas')
    var ctx=canvas.getContext('2d');
    ctx.font = this.font;
    this.text=this.wrap_text(ctx,text,this.original_width);
    this.width=0;
    this.text.forEach(function(str) {
        var w=ctx.measureText(str).width;
        if (that.width<w) {
            that.width=w;
        }
    });
    this.draw_me=true;
    // number of lines * height
    this.height=this.text.length*this.text_height; 
    this.height*=this.bottom_add;
    this.centre.x=this.width/2;
}

truffle.textbox.prototype.recalc_bbox=function() {
    this.last_bbox=[this.bbox[0],this.bbox[1],this.bbox[2],this.bbox[3]];
    var safe=5;
    var l=this.pos.x-safe;
    var t=this.pos.y-safe;
    if (this.parent_transform) {
        l+=this.parent_transform.m[4];
        t+=this.parent_transform.m[5];
    }
    l+=-this.centre.x;
    t-=this.text_height;
    this.bbox=[~~(l+0.5),~~(t+0.5),
               ~~(l+this.width+safe*2+0.5),
               ~~(t+this.height+safe*2+0.5)]; 
}

truffle.textbox.prototype.draw=function(ctx) {
    truffle.drawable.prototype.draw.call(this,ctx);
    if (this.hidden) return;

    if (this.text.length==0) {
        this.draw_me=false;
        return;
    }

    // two render paths
    if (this.complex_transform || this.parent_transform) {
        ctx.save();
/*        ctx.transform(this.transform.m[0],
                      this.transform.m[1],
                      this.transform.m[2],
                      this.transform.m[3],
                      this.transform.m[4],
                      this.transform.m[5]);
        
        if (this.parent_transform!=null) {
            ctx.transform(this.parent_transform.m[0],
                          this.parent_transform.m[1],
                          this.parent_transform.m[2],
                          this.parent_transform.m[3],
                          this.parent_transform.m[4],
                          this.parent_transform.m[5]);
        }*/

        ctx.font = this.font;
        var y=0;
        var that=this;

        ctx.translate(this.parent_transform.m[4]+this.pos.x,
                      this.parent_transform.m[5]+this.pos.y);


        ctx.fillStyle = "#fff";
        ctx.globalAlpha=0.75;

        ctx.fillRect(~~(-this.centre.x+0.5), 
                     ~~(-this.text_height), 
                     ~~(this.width+0.5),~~(this.height+0.5)); 

        ctx.globalAlpha=1;

        ctx.fillStyle = this.text_colour;
        ctx.textAlign = "left";
        this.text.forEach(function(text) {
            ctx.fillText(text,
                         ~~(-that.centre.x+0.5),
                         ~~(y-that.centre.y+0.5));
            y+=that.text_height;
        });

        ctx.restore();
    }
    else // simple render path
    {
        ctx.font = this.font;
        var y=0;
        var that=this;

        ctx.fillStyle = "#fff";
        ctx.globalAlpha=0.75;

        ctx.fillRect(~~(this.pos.x-this.centre.x+0.5), 
                     ~~(this.pos.y-this.height+0.5), 
                     ~~(this.width+0.5),~~(this.height*this.bottom_add+0.5));
        ctx.globalAlpha=1;

        ctx.fillStyle = this.text_colour;
        ctx.textAlign = "left";
        this.text.forEach(function(text) {
            ctx.fillText(text,
                         ~~(that.pos.x-that.centre.x+0.5),
                         ~~((that.pos.y-that.centre.y)+y+0.5));
            y+=that.text_height;
        });
    }


    if (this.draw_bb) 
    {
        // draw bbox
        ctx.beginPath();
        ctx.strokeStyle = "#00ffff";
        var bb=this.get_bbox();
        ctx.rect(bb[0], bb[1], bb[2]-bb[0], bb[3]-bb[1]); 
        ctx.stroke();
    }
    
    this.recalc_bbox();
    this.draw_me=false;
}

