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

truffle.main={}
truffle.main.world=null;

var requestAnimationFrame = window.requestAnimationFrame || window.mozRequestAnimationFrame ||  
    window.webkitRequestAnimationFrame || window.msRequestAnimationFrame;

var update_fn;
var last_time=0;
var frames;
var total_time;
var update_time;
var update_frames;
var canvas;
var ctx;

truffle.main.init=function(game_create,game_update) {
    canvas=document.getElementById('canvas')
    ctx=canvas.getContext('2d');

    last_time = (new Date()).getTime();
    frames = 0;
    total_time = 0;
    update_time = 0;
    update_frames =0;

    truffle.main.world=new truffle.world();
    game_create();
    update_fn=game_update;
    requestAnimationFrame(truffle.main.loop);
}

function ipad_emulator(millis)
{
    var date = new Date();
    var curDate = null;
    do { curDate = new Date(); }
    while(curDate-date < millis);
}

truffle.main.loop=function(timestamp) {
    var now = (new Date()).getTime();
    var delta = now-last_time;
   
    update_fn(now/1000,delta/1000);
    truffle.main.world.update(now/1000,delta/1000);

    if (ctx.canvas.width!=window.innerWidth) {
        truffle.main.world.canvas_state.resize(
            window.innerWidth,
            window.innerHeight);
        truffle.main.world.redraw();
    }

    last_time = now;
    total_time+=delta;
    frames++;
    update_time+=delta;
    update_frames++;

    if(update_time > 1000) {
        var av=(1000*frames/total_time);
        av=Math.round(av*100)/100;
        var cur=(1000*update_frames/update_time);
        cur=Math.round(cur*100)/100;

        document.getElementById('fps').innerHTML = "fps avg: " + av + " cur: " + cur;
        update_time = 0;
        update_frames =0;
    }

//    ipad_emulator(50);
    requestAnimationFrame(truffle.main.loop);
}

