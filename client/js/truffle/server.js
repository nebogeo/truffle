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

/////////////////////////////////////////////

truffle.server=function(URL,on_open) {
    this.socket = new WebSocket(URL);
    this.listeners = {};
    this.ready=false;
    var that=this;

    this.socket.onmessage= function(s) {
        var data=JSON.parse(s.data);
        if (that.listeners[data.f]!=null)
        {
            that.listeners[data.f](JSON.parse(data.a));
        }
    };

    this.socket.onopen = function() {
        on_open();
    };
}

truffle.server.prototype.call = function(name,args) {
    this.socket.send(JSON.stringify({"f":name, "a":args}));
};

truffle.server.prototype.listen = function(name,fn) {
    this.listeners[name]=fn;
}


