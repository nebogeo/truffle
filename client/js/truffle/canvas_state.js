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

truffle.canvas_state=function() {
    this.mouse_changed=false;
    this.mouse_down=false;
    this.mouse_x=0;
    this.mouse_y=0;
    this.canvas=document.getElementById('canvas');
    this.ctx = this.canvas.getContext('2d');         
    this.bg_colour = "#ffffff";
    
    this.ctx.mozImageSmoothingEnabled = false;
    this.ctx.webkitImageSmoothingEnabled=false

    // start off at a reasonably centred position
    this.world_x=-800;
    this.world_y=200;
    this.world_desired_x=0;
    this.world_desired_y=0;
    this.world_offset_x=0;
    this.world_offset_y=300;

    // for world to refresh areas needed by scrolling screen
    this.refresh_top=0;
    this.refresh_bottom=0;
    this.refresh_left=0;
    this.refresh_right=0;

/*
    var that=this;
    this.bgimage=new Image();
    this.bgimage.onload = function() {
        that.clear_screen();
    };
    this.bgimage.onerror = function(e) {
        log("could't load "+url);
    }
    this.bgimage.src = "../game/images/bg.png";  
*/


    var _this=this;

    this.canvas.addEventListener('touchstart',function(e) {
        _this.mouse_changed=true;
        _this.mouse_down=true;
    }, false);

    this.canvas.addEventListener('touchmove',function(e) {
        _this.update_mouse(e);
    }, false);

    this.canvas.addEventListener('touchend',function(e) {
        _this.mouse_changed=true;
        _this.mouse_down=false;
    }, false);

//---------------

    this.canvas.addEventListener('mousedown', function(e) {
        _this.mouse_changed=true;
        _this.mouse_down=true;
    });

    this.canvas.addEventListener('mousemove', function(e) {
        _this.update_mouse(e);
    });

    this.canvas.addEventListener('mouseup', function(e) {
        _this.mouse_changed=true;
        _this.mouse_down=false;
    });
}

truffle.canvas_state.prototype.resize=function(w,h) {
    this.world_offset_x=w/2;
    //this.world_offset_y=h/2;
    this.canvas.width=w;
    this.canvas.height=h;
}

truffle.canvas_state.prototype.clear_screen=function() {
    //this.ctx.clearRect(0,0,this.canvas.width,this.canvas.height);
    this.ctx.fillStyle = this.bg_colour;
    this.ctx.fillRect(0,0,this.canvas.width,this.canvas.height);
    //this.ctx.drawImage(this.bgimage,0,0);
}

truffle.canvas_state.prototype.begin_scene=function() {
    //this.Ctx.clearRect(0,0,720,576);
    this.ctx.save();
    this.ctx.fillStyle = "#000000";
    this.ctx.strokeStyle = "#000000";  
    this.ctx.translate(this.world_x+this.world_offset_x,
                       this.world_y+this.world_offset_y);

    // debug bbox mode
    //this.ctx.globalCompositeOperation = 'xor';
}

truffle.canvas_state.prototype.end_scene=function(delta) {
    this.ctx.restore();
    this.update_world_pos(delta);
}

truffle.canvas_state.prototype.clear_rects=function(bboxes) {
    this.ctx.fillStyle = this.bg_colour;
    var that=this;
    bboxes.forEach(function(box) {
        that.ctx.fillRect(~~(box[0]+2),~~(box[1]+2),
                          ~~((box[2]-box[0])-4),
                          ~~((box[3]-box[1])-4)); 
    });
}

truffle.canvas_state.prototype.set_clip=function(bboxes) {
    this.ctx.save();
/*
    var that=this;
    bboxes.forEach(function(box) {
        that.ctx.rect(~~(box[0]+2),~~(box[1]+2),
                      ~~((box[2]-box[0])-4),
                      ~~((box[3]-box[1])-4)); 
    });
    that.ctx.stroke();
*/

//    this.ctx.save();

    // Set the clipping area
    this.ctx.beginPath();
    var that=this;
    bboxes.forEach(function(box) {
        that.ctx.rect(~~(box[0]+0.5),~~(box[1]+0.5),
                      ~~(0.5+(box[2]-box[0])),
                      ~~(0.5+(box[3]-box[1])));
    });
    this.ctx.clip();
//    this.ctx.restore();

}

truffle.canvas_state.prototype.unclip=function() {
    this.ctx.restore();
}

truffle.canvas_state.prototype.update=function() {
    this.mouse_changed=false;
}

truffle.canvas_state.prototype.move_world_to=function(x,y) {
    this.world_desired_x=x;
    this.world_desired_y=y;
}

// scroll screen
truffle.canvas_state.prototype.update_world_pos=function(delta) {
    var d=new truffle.vec2(this.world_x+this.world_desired_x,
                           this.world_y+this.world_desired_y);
    
    this.refresh_left=0;
    this.refresh_right=0;
    this.refresh_top=0;
    this.refresh_bottom=0;

    var speed=200*delta;

    if (d.mag()>speed)
    {
        d=d.normalise().mul(-speed);

        if (d.x<0) this.refresh_right=-d.x;
        else this.refresh_left=d.x;
        if (d.y<0) this.refresh_bottom=-d.y;
        else this.refresh_top=d.y;

        var sx=0;
        var dx=d.x;
        var width=this.ctx.canvas.width-d.x;
        if (d.x<0) {
            sx=-d.x;
            dx=0;
            width=this.ctx.canvas.width+d.x;
        }
        
        var sy=0;
        var dy=d.y;
        var height=this.ctx.canvas.height-d.y;
        if (d.y<0) {
            sy=-d.y;
            dy=0;
            height=this.ctx.canvas.height+d.y;
        }
        
        this.ctx.drawImage(this.ctx.canvas,
                           ~~(sx),~~(sy),~~(width),~~(height),
                           ~~(dx),~~(dy),~~(width),~~(height));
               
        this.world_x+=~~(d.x);
        this.world_y+=~~(d.y);
    }
}

// Creates an object with x and y defined,
// set to the mouse position relative to the state's canvas
// If you wanna be super-correct this can be tricky,
// we have to worry about padding and borders
truffle.canvas_state.prototype.update_mouse = function(e) {
    var element = this.canvas, offsetX = 0, offsetY = 0;
 
    // Compute the total offset
/*    if (element.offsetParent !== undefined) {
        do {
            offsetX += element.offsetLeft;
            offsetY += element.offsetTop;
        } while ((element = element.offsetParent));
    }
  */  
    // Add padding and border style widths to offset
    // Also add the <html> offsets in case there's a position:fixed bar
    //offsetX += this.stylePaddingLeft + this.styleBorderLeft + this.htmlLeft;
    //offsetY += this.stylePaddingTop + this.styleBorderTop + this.htmlTop;
    
    this.mouse_x = e.pageX - offsetX;
    this.mouse_y = e.pageY - offsetY;

    this.mouse_x -= (this.world_x+this.world_offset_x);
    this.mouse_y -= (this.world_y+this.world_offset_y);

}

truffle.canvas_state.prototype.stats=function(num) {
    this.ctx.fillStyle="#000000";
    this.ctx.fillRect(10,10,100,10);

    this.ctx.fillStyle="#ff0000";
    var s=num*100;
    this.ctx.fillRect(11,11,s,8);
}

////////////////////////////////////////////

