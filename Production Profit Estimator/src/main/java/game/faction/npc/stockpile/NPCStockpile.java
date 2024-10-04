package game.faction.npc.stockpile;

import java.io.IOException;

import game.GAME;
import game.GAME_LOAD_FIXER;
import game.GameDisposable;
import game.VERSION;
import game.faction.FACTIONS;
import game.faction.npc.FactionNPC;
import game.faction.npc.NPCResource;
import game.faction.npc.stockpile.UpdaterTree.ResIns;
import game.time.TIME;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import settlement.entity.ENTETIES;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.file.SAVABLE;
import snake2d.util.misc.ACTION;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.LISTE;
import util.data.DOUBLE;
import util.statistics.HistoryResource;
import view.interrupter.IDebugPanel;
import world.region.RD;
import world.region.pop.RDRace;

public class NPCStockpile extends NPCResource{

    public static final int AVERAGE_PRICE = 200;
    private static final double PRICE_MAX = 10.0;
    private static final double PRICE_MIN = 1.0/PRICE_MAX;
    private static final double PILE_SIZE = 25*ENTETIES.MAX/40000.0;
//	private static final double WORK_SPEED = 0.07*ENTETIES.MAX/40000.0;
    private static final double PRICE_ELASTICITY = 1; // 0 to 1, lower being more static, higher more variable

    static Updater updater;

    static {
        new GameDisposable() {

            @Override
            protected void dispose() {
                updater = null;
            }
        };
    }

    private final SRes[] resses = new SRes[RESOURCES.ALL().size()];
    final FactionNPC f;
    private final DOUBLE credits;
    private double workforce = 1;

    public final HistoryResource price = new HistoryResource(16, TIME.seasons(), true);
    public final HistoryResource forSale = new HistoryResource(16, TIME.seasons(), true);

    public NPCStockpile(FactionNPC f, LISTE<NPCResource> all, DOUBLE credits){
        super(all);
        this.f = f;
        ACTION a = new ACTION() {

            @Override
            public void exe() {
                for (FactionNPC f : FACTIONS.NPCs()) {
                    f.stockpile.saver().clear();
                    f.stockpile.update(f, 0);
                    f.credits().set(0);
                }
                GAME.factions().prime();
            }
        };

        if (updater == null) {
            updater = new Updater();
            if (VERSION.versionIsBefore(67, 35))
                new GAME_LOAD_FIXER() {

                    @Override
                    protected void fix() {
                        a.exe();

                    }
                };
        }
        IDebugPanel.add("TRADE RESET", a);

        this.credits = credits;
        for (RESOURCE res : RESOURCES.ALL()) {
            resses[res.index()] = new SRes();
        }
    }

    public int amount(RESOURCE res) {
        if (res == null) {
            int tt = 0;
            for (int ri = 0; ri < RESOURCES.ALL().size(); ri++)
                tt += resses[ri].tradeAm();
            return tt;
        }
        return (int)resses[res.index()].tradeAm();
    }

    public int amount(int ri) {
        return (int)resses[ri].tradeAm();
    }

    public void inc(RESOURCE res, double amount) {
        resses[res.index()].trade(amount);
    }

    public double creditScore() {
        double aa = workforce*AVERAGE_PRICE*RESOURCES.ALL().size();
        aa = (aa + credits.getD())/(aa+1);
        aa = CLAMP.d(aa, PRICE_MIN, PRICE_MAX);
        return aa;
    }

    public double credit() {
        return (workforce*AVERAGE_PRICE*RESOURCES.ALL().size() + credits.getD());
    }

    public double price(int ri, double amount) {

        double mul = f.race().pref().priceMul(RESOURCES.ALL().get(ri));

        SRes r = resses[ri];
        double before = r.price();
        double after = r.priceAt(r.amTarget()+r.traded()+amount);
        double price = before + (after-before)*0.5;
        price *= creditScore();
        return mul*price;
    }

    public double priceBuy(int ri, double amount) {

        double price = price(ri, amount);
        price *= f.race().pref().priceCap(RESOURCES.ALL().get(ri));


        return price*amount;
    }

    public double priceSell(int ri, double amount) {

        double price = price(ri, -amount);

        return price*amount;

    }

    public double prodRate(RESOURCE res) {
        return resses[res.index()].totRate;
    }

    public double rate(RESOURCE res) {
        return resses[res.index()].rate;
    }

    @Override
    protected SAVABLE saver() {
        return new SAVABLE() {

            @Override
            public void save(FilePutter file) {
                RESOURCES.map().saver().save(resses, file);
                file.d(workforce);
                price.save(file);
                forSale.save(file);
            }

            @Override
            public void load(FileGetter file) throws IOException {
                RESOURCES.map().loader().load(resses, file);
                workforce = file.d();
                if (!VERSION.versionIsBefore(67, 11)) {
                    price.load(file);
                    forSale.load(file);
                }
            }

            @Override
            public void clear() {
                for(SRes r : resses)
                    r.clear();
                workforce = 1;
                price.clear();
                forSale.clear();
            }
        };
    }

    @Override
    public void update(FactionNPC faction, double seconds) {
        update(faction, seconds, RD.RACES().population.get(faction.capitolRegion()));
    }

    public void update(FactionNPC faction, double seconds, double wf) {
        updater.tree.update(faction);

        //int wf =  RD.RACES().population.get(faction.capitolRegion());
        //wf *= 0.75 + 0.25*(BOOSTABLES.NOBLE().COMPETANCE.get(faction.court().king().roy().induvidual));


        workforce = wf*PILE_SIZE/RESOURCES.ALL().size();
        for (RESOURCE res : RESOURCES.ALL()) {
            UpdaterTree.TreeRes o = updater.tree.o(res);
            double prod = 0;
            double prodTot = 0;
            double sp = 1;
            for (ResIns r : o.producers) {
                prod = Math.max(prod, 1.0/r.prodSpeedBonus);
                double t = 1.0/r.prodSpeedTot;
                if (t > prodTot) {
                    sp = r.rateSpeed;
                    prodTot = t;
                }
            }
            resses[res.index()].rateSpeed = sp;
            resses[res.index()].rate = prod;
            resses[res.index()].totRate = prodTot;
        }

        updater.equalize(this, seconds*TIME.secondsPerDayI);

        for (RESOURCE res : RESOURCES.ALL()) {
            price.set(res, (int) Math.round(res(res.index()).price()));
            forSale.set(res, (int) Math.round(res(res.index()).tradeAm()+res(res.index()).traded()));
        }

    }



    @Override
    protected void generate(RDRace race, FactionNPC faction, boolean init) {
        saver().clear();
        update(faction, 0);

    }

    SRes res(int index) {
        return resses[index];
    }

    final class SRes implements SAVABLE{

        private double totRate = 1;
        private double rate = 1;
        private double rateSpeed = 1;
        private double traded = 0;

        public double rate() {
            return rate;
        }

        public double rateSpeed() {
            return rateSpeed;
        }

        public double rateTot() {
            return totRate;
        }

        public double traded() {
            return traded;
        }

        public double amTarget() {
            return 1 + rate*workforce;
        }

        public double tradeAm() {
            return Math.max(totRate*workforce+traded, 0);
        }

        public double amMul(double amount) {
            double tar = amTarget();
            if (amount <= 0)
                return PRICE_MAX;
            tar /= amount;
            tar = Math.pow(tar, PRICE_ELASTICITY);
            tar = CLAMP.d(tar, PRICE_MIN, PRICE_MAX);
            return tar;

        }

        public double amMul() {
            return amMul(amTarget()+traded());


        }

        public double priceBase() {
            if (totRate == 0)
                return AVERAGE_PRICE*10000;
            return AVERAGE_PRICE/totRate;
        }

        public double price() {
            return priceAt(amTarget()+traded);
        }

        public double priceAt(double amount) {
            return amMul(amount)*priceBase();

        }

        void trade(double am) {
            traded += am;
        }

        void tradeSet(double am) {
            traded = am;
        }

        @Override
        public void save(FilePutter file) {
            file.d(totRate);
            file.d(rate);
            file.d(traded);
        }

        @Override
        public void load(FileGetter file) throws IOException {
            totRate = file.d();
            rate = file.d();
            traded = file.d();
        }

        @Override
        public void clear() {
            totRate = 1;
            rate = 1;
            traded = 0;
            rateSpeed = 1;
        }

    }

    static double WV = 9.99;
    static double WVt = 10;

    public static void main(String[] args) {

        System.out.println("1 wood = 1WV, 1 furniture = " + WV + "WV + " + (10-WV) + "wood, A: export wood, B: make wood, export furniture, C: import wood export furniture");

        double toll = 3;
        double tariff = 0.5;


        toll = 2;
        tariff = 0.5;
        debug(toll, tariff, toll, tariff*WV/10, true);
        debug(toll*0.5, tariff*0.5, toll*0.5, tariff*0.5*WV/10, true);
        debug(toll*0.25, tariff*0.25, toll*0.25, tariff*0.25*WV/10, true);
        System.out.println();

//		debug(0, 0, 0, 0, true);
//
//
//		debug(0, 0.5, 0, WV*0.5/WVt, true);
//
//
//
//		double t1 = 0.05;
//		double t2 = 0.05 + 0.5*(1-WV/10.0);
//
//		debug(4, t1, 4, t2, true);
//		debug(2, t1, 2, t2, true);
//		debug(0, t1, 0, t2, false);
    }

    public static void debug(double tollAddA, double tollMulA, double tollAddB, double tollMulB, boolean export) {
        System.out.println("wood: " + "x" + tollMulA + " +" + tollAddA + ", " + "furniture: " + "x" + tollMulB + " +" + tollAddB + ", " + " " + (export ? "pay import" : "free import"));
        System.out.println("A: " + profit(10, 10, tollMulA, tollAddA, true));

        System.out.println("B: " + profit(1.0, 10*WVt, tollMulB, tollAddB, true));
        System.out.println("C: " + (-profit(10*(10-WV)/WV, 10, -tollMulA, -tollAddA, export) + profit(1.0*10/WV, 100, tollMulB, tollAddB, true)));
    }

    public static double profit(double am, double price, double mul, double add, boolean export) {
        double p = am*price;
        if (export) {
            p -= p*mul;

        }
        p -= am*add;
        return p;
    }



}
