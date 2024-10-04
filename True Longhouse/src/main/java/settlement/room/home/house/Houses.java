package settlement.room.home.house;

import snake2d.util.sets.ArrayList;

final class Houses {
	// LONGHOUSE_MOD Changed HomeHouse from 8 to 10, citizen/slave long house,
	private final HomeHouse[] houses = new HomeHouse[10];
	private ArrayList<HomeHouse> free = new ArrayList<>(houses.length);
	private static final StackTraceElement[] ee = new StackTraceElement[0];
	
	Houses(){
		for (int i = 0; i < houses.length; i++) {
			houses[i] = new HomeHouse();
			houses[i].els = ee;
		}
	}
	
	void ret(HomeHouse h) {
		h.user = null;
		free.add(h);
	}
	
	void update() {
		free.clearSloppy();
		for (int i = 0; i < houses.length; i++) {
			if (houses[i].user != null) {
				for (StackTraceElement ee : houses[i].els)
					System.err.println(ee);
				throw new RuntimeException(" " + houses[i].user);
			}
			
			free.add(houses[i]);
		}
	}
	
	void clear() {
		free.clearSloppy();
		for (int i = 0; i < houses.length; i++) {
			houses[i].user = null;
			houses[i].els = ee;
			free.add(houses[i]);
		}
	}
	
	HomeHouse get(int tx, int ty, Object user) {
		if (free.isEmpty()) {
			
			for (HomeHouse h : houses) {
				
				System.err.println(h.user);
				for (StackTraceElement ee : h.els)
					System.err.println(ee);
				System.err.println();
				
			}
			throw new RuntimeException("no houses!");
		}
		HomeHouse h = free.removeLast();
		h.useAndReserve(tx, ty);
		h.user = user;
//		if (VERSION.versionIs(11, 10))
//			h.els = new RuntimeException().getStackTrace();
		return h;
		
	}
	
}
