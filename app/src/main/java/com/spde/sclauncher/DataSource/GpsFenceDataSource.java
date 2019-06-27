package com.spde.sclauncher.DataSource;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.spde.sclauncher.net.pojo.CrossFenceInfo;
import com.spde.sclauncher.net.pojo.LatLongPoint;
import com.spde.sclauncher.net.pojo.PeriodWeekly;
import com.spde.sclauncher.net.pojo.RegionLimit;
import com.spde.sclauncher.net.pojo.RegionPolygon;
import com.spde.sclauncher.net.pojo.RegionRectangle;
import com.spde.sclauncher.net.pojo.RegionRound;
import com.spde.sclauncher.net.pojo.RegionShape;
import com.spde.sclauncher.provider.SCDB;
import com.yynie.myutils.Logger;
import com.yynie.myutils.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.spde.sclauncher.SCConfig.SUPPORTED_FENCE_SHAPE;

public class GpsFenceDataSource extends AbstractDataSource {
    private final Logger log = Logger.get(GpsFenceDataSource.class, Logger.Level.DEBUG);
    private static GpsFenceDataSource sInstance;
    private List<FenceItem> itemList = new ArrayList<FenceItem>();
    private Pattern nmeaPattern = Pattern.compile("0([EW])(\\d{1,3}\\.\\d+)([SN])(\\d{1,3}\\.\\d+)T");

    class FenceItem{
        int id;
        int type;
        RegionShape shape;
        List<PeriodWeekly> periodList;

        public FenceItem(int id, int type, RegionShape shape, List<PeriodWeekly> periodList) {
            this.id = id;
            this.type = type;
            this.shape = shape;
            this.periodList = periodList;
        }
    }

    public static GpsFenceDataSource getInstance(){
        synchronized (GpsFenceDataSource.class){
            if(sInstance == null){
                sInstance = new GpsFenceDataSource();
            }
            return sInstance;
        }
    }

    @Override
    protected void prepareOnInit() {
        //TODO: 读出数据？？
    }

    @Override
    public void release() {

    }

    @Override
    public void restore() {
        deleteAll();
        deleteAllCross();
    }

    public void remove(int opType, RegionLimit regionLimit){
        //opType 请求状态：1表示父亲卡 2表示母亲卡
        synchronized (itemList) {
            int regId = regionLimit.getNo();
            Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/fence");
            String where = SCDB.Fence.REGID + "=" + regId;
            int delnum = getContext().getContentResolver().delete(uri, where, null);
            log.d("remove RegId=" + regId + ", delnum=" + delnum);
            if(delnum > 0){
                itemList.clear();
            }
            Uri crossUri = Uri.parse("content://" + SCDB.AUTHORITY + "/crossfence");
            where = SCDB.CrossFence.REGID + "=" + regId;
            delnum = getContext().getContentResolver().delete(crossUri, where, null);
            log.d("remove crossfence RegId=" + regId + ", delnum=" + delnum);
        }
    }

    public List<CrossFenceInfo> checkFence(String nmea){
        //0E121.411783N31.178125Txxxx
        Matcher matcher = nmeaPattern.matcher(nmea.trim());
        String longString = null, latString = null;
        int longSymbol = 1, latSymbol = 1;
        if(matcher.find()){
            String EW = matcher.group(1);
            if(EW.equals("W")) longSymbol = -1;
            longString = matcher.group(2);
            String SN = matcher.group(3);
            if(SN.equals("S")) latSymbol = -1;
            latString = matcher.group(4);
        }
        List<CrossFenceInfo> ret = new ArrayList<CrossFenceInfo>();
        if(StringUtils.isNoneBlank(longString, latString)){
            try {
                double longitude = Double.parseDouble(longString) * longSymbol;
                double latitude = Double.parseDouble(latString) * latSymbol;
                List<CrossFenceInfo> infoList = checkFence(latitude, longitude);
                for(CrossFenceInfo info:infoList){
                    CrossFenceInfo needReport = saveCrossFenceInfo(info);
                    if(needReport != null) ret.add(needReport);
                }
            }catch (NumberFormatException e){
                log.e("checkFence parse nmea failed: " + nmea + "e:" + e.getMessage());
            }
        }else{
            log.e("checkFence parse nmea failed: " + nmea);
        }
        return ret;
    }

    private CrossFenceInfo saveCrossFenceInfo(CrossFenceInfo info){
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/crossfence");
        Cursor cursor = getContext().getContentResolver().query(uri, null,
                SCDB.CrossFence.REGID + "=" + info.getId(),null, null);
        boolean exist = false;
        if(cursor.moveToNext()){
            exist = true;
        }
        cursor.close();
        log.i(" Cross Fence(" + info.getId() +  "):: previous state is " + (exist?"OUT":"IN") + " current state is " + (info.isInFence()?"IN":"OUT"));
        //离开围栏的记录会存进crossfence, 存在regid对应的记录则说明上一次是在围栏外
        if(exist && info.isInFence()){ //从围栏外进入需上报
            log.i(" Cross Fence :: go into fence from outer, need report");
            String where = SCDB.CrossFence.REGID + "=" + info.getId();
            int delnum = getContext().getContentResolver().delete(uri, where,null);
            log.d("saveCrossFenceInfo gps in ,remove RegId=" + info.getId() + ", delnum=" + delnum);
            return info;
        }
        if(!exist && !info.isInFence()){ //离开
            log.i(" Cross Fence :: go out of fence from inner, need report");
            ContentValues values = new ContentValues();
            values.put(SCDB.CrossFence.REGID, info.getId());
            values.put(SCDB.CrossFence.UPDATETIME, System.currentTimeMillis()/1000L);
            Uri insertedItemUri = getContext().getContentResolver().insert(uri, values);
            log.d("saveCrossFenceInfo insertedItemUri=" + insertedItemUri);
            return info;
        }
        return null;
    }

    private List<CrossFenceInfo> checkFence(double latitude, double longitude){
        List<FenceItem> items = getItemList();
        List<CrossFenceInfo> ret = new ArrayList<CrossFenceInfo>();
        if(items.size() == 0) return ret;
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        String dayOfWeekString = String.valueOf(dayOfWeek -1);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int min = calendar.get(Calendar.MINUTE);
        int minOfDay = hour * 60 + min;
        log.i("checkFence fence item's size= " + items.size());
        for(FenceItem one:items){
            if(one.periodList.size() == 0) continue;
            if(!one.periodList.get(0).getWeeks().contains(dayOfWeekString)){
                log.w("checkFence fenceId=" + one.id + ", current day is not in week day");
                continue;
            } 
            boolean found = false;
            for(PeriodWeekly pw:one.periodList){
                if(minOfDay > pw.getStartMinute() && minOfDay < pw.getEndMinute()) {
                    found = true;
                    break;
                }else{
                    log.w("checkFence fenceId=" + one.id + ", not in time period");
                }
            }
            if(found){
                CrossFenceInfo info = new CrossFenceInfo(one.id, one.type);
                boolean inOrOut = isInFence(latitude, longitude, one.shape);
                log.i("checkFence fenceId=" + one.id + ", inOrOut=" + inOrOut);
                info.setInFence(inOrOut);
                ret.add(info);
            }
        }
        return ret;
    }

    private boolean isInFence(double lat, double lng, RegionShape shape){
        if(shape instanceof RegionRound){
            return isInCircle(((RegionRound) shape).getRadius(),
                    ((RegionRound) shape).getCenter().getLatitude(), ((RegionRound) shape).getCenter().getLongitude(),
                    lat, lng);
        }else if(shape instanceof RegionPolygon){
            return isInPolygon(((RegionPolygon) shape).getPointList(), lat, lng);
        }else{
            throw new RuntimeException("Not support Shapes other then (" + SUPPORTED_FENCE_SHAPE + ")");
        }
    }

    private boolean isInCircle(int radius, double centerLat, double centerLng, double lat, double lng){
        log.d("isInCircle center=(" + centerLng + "," + centerLat + ") , curpoint=(" + lng + "," + lat + ")");
        double EARTH_RADIUS = 6378137.0;//6371004.0;
        double radCenLat = centerLat * Math.PI / 180.0;
        double radCenLng = centerLng * Math.PI / 180.0;
        double radLat = lat * Math.PI / 180.0;
        double radLng = lng * Math.PI / 180.0;

        double a = radCenLat - radLat;
        double b = radCenLng - radLng;
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a/2),2) +
                Math.cos(radCenLat)*Math.cos(radLat)*Math.pow(Math.sin(b/2),2)));
        s = s * EARTH_RADIUS;
        s = Math.round(s * 10000) / 10000;
        log.i("isInCircle radius = " + radius + " distance=" + s);
        return (s <= radius);
    }

    private boolean isInPolygon(List<LatLongPoint> vertexList, double lat, double lng){
        int vertexCount = vertexList.size();
        if(vertexCount < 3){
            return false;
        }
        int count = 0;
        for (int I = 0, J = vertexCount - 1; I < vertexCount; J = I++) {
            LatLongPoint vI = vertexList.get(I);
            LatLongPoint vJ = vertexList.get(J);
            double vI_x = vI.getLongitude();
            double vI_y = vI.getLatitude();
            double vJ_x = vJ.getLongitude();
            double vJ_y = vJ.getLatitude();
            double px = lng;
            double py = lat;

//            if((vI_x == px && vI_y == py) || (vJ_x == px && vJ_y == py)) {
//                return true; //on vertex
//            }

            if((vI_x >= px) == (vJ_x >= px)){
                continue; //on same side
            }

            double crossy = (vJ_y - vI_y) * (px - vI_x) / (vJ_x - vI_x) + vI_y;
            if(py <= (vJ_y - vI_y) * (px - vI_x) / (vJ_x - vI_x) + vI_y){
                count ++;
            }

        }
        log.i("isInPolygon vertexCount=" + vertexCount + " ,curpoint=(" + lng + "," + lat + ") ,crosscount=" + count);
        return (count % 2) == 1;
    }

    public void addOrUpdate(int opType, RegionLimit regionLimit){
        //opType 请求状态：1表示父亲卡 2表示母亲卡
        synchronized (itemList) {
            int regId = regionLimit.getNo();
            RegionShape shape = regionLimit.getShape();
            List<PeriodWeekly> periodList = regionLimit.getPeriodList();
            if (periodList.size() == 0) {
                log.e("addOrUpdate periodList in EMPTY");
                return;
            }

            String weeks = periodList.get(0).getWeeks();
            String timeString = "";
            for (PeriodWeekly p : periodList) {
                timeString += p.getStartMinute() + "-" + p.getEndMinute() + "+";
            }
            boolean insert = true;
            Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/fence");
            Cursor cursor = getContext().getContentResolver().query(uri, new String[]{SCDB.Fence.REGID},
                    SCDB.Fence.REGID + "=" + regId, null, null);
            if (cursor.getCount() > 0) {
                insert = false;
            }
            cursor.close();
            ContentValues values = new ContentValues();
            if (insert) values.put(SCDB.Fence.REGID, regId);
            values.put(SCDB.Fence.REGTYPE, opType);
            values.put(SCDB.Fence.SHAPE, shape.getName());
            values.put(SCDB.Fence.ELES, shape.getElementsInString());
            values.put(SCDB.Fence.TIME, timeString);
            values.put(SCDB.Fence.DAY, weeks);
            if (insert) {
                Uri insertedItemUri = getContext().getContentResolver().insert(uri, values);
                log.d("addOrUpdate insertedItemUri=" + insertedItemUri);
            } else {
                int updateRow = getContext().getContentResolver().update(uri, values, SCDB.Fence.REGID + "=" + regId, null);
                log.d("addOrUpdate updateRow=" + updateRow);
            }
            itemList.clear();
        }
    }

    private RegionShape createShape(String shape){
        if(StringUtils.equalsIgnoreCase(shape, "Round")){
            return new RegionRound();
        }else if(StringUtils.equalsIgnoreCase(shape, "Rectangle")){
            return new RegionRectangle();
        }else if(StringUtils.equalsIgnoreCase(shape, "Polygon")){
            return new RegionPolygon();
        }
        return null;
    }

    private final List<FenceItem> getItemList(){
        synchronized (itemList) {
            if (itemList.isEmpty()) {
                Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/fence");
                String where = SCDB.Fence.SHAPE + " IN (" + SUPPORTED_FENCE_SHAPE  + ")";
                Cursor cursor = getContext().getContentResolver().query(uri, null,
                        where,null, null);
                while(cursor.moveToNext()){
                    int id = cursor.getInt(cursor.getColumnIndex(SCDB.Fence.REGID));
                    int type = cursor.getInt(cursor.getColumnIndex(SCDB.Fence.REGTYPE));
                    String shape = cursor.getString(cursor.getColumnIndex(SCDB.Fence.SHAPE));
                    String eles = cursor.getString(cursor.getColumnIndex(SCDB.Fence.ELES));
                    String[] eleArray = eles.split("\\*");
                    RegionShape RShape = createShape(shape);
                    if(RShape != null){
                        RShape.build(eleArray);
                    }
                    String timestr = cursor.getString(cursor.getColumnIndex(SCDB.Fence.TIME));
                    String[] timeArr = timestr.split("\\+");
                    String weeks = cursor.getString(cursor.getColumnIndex(SCDB.Fence.DAY));
                    List<PeriodWeekly> periodList = new ArrayList<PeriodWeekly>();
                    for(String time:timeArr){
                        if(StringUtils.isNotBlank(time)){
                            String[] timesets = time.split("-");
                            if(timesets.length >= 2){
                                try {
                                    int startmin = Integer.parseInt(timesets[0].trim());
                                    int endmin = Integer.parseInt(timesets[1].trim());
                                    PeriodWeekly p = new PeriodWeekly(startmin, endmin, weeks);
                                    periodList.add(p);
                                }catch (NumberFormatException e){
                                    log.e("Can't parse time: " + e.getMessage());
                                }
                            }
                        }
                    }
                    FenceItem one = new FenceItem(id, type, RShape, periodList);
                    itemList.add(one);
                }
                cursor.close();
            }
            List<FenceItem> retlist = new ArrayList<FenceItem>();
            for(FenceItem item: itemList){
                retlist.add(item);
            }
            return retlist;
        }
    }

    private void deleteAll(){
        synchronized (itemList) {
            Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/fence");
            int delnum = getContext().getContentResolver().delete(uri, null, null);
            log.d("deleteAll delnum=" + delnum);

            if(delnum > 0) {
                itemList.clear();
            }
        }
    }

    private void deleteAllCross(){
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/crossfence");
        int delnum = getContext().getContentResolver().delete(uri, null, null);
        log.d("deleteAllCross delnum=" + delnum);
    }
}
