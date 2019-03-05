package com.spde.sclauncher.net.codec;

import android.util.Base64;

import com.sonf.core.buffer.IoBuffer;
import com.sonf.core.buffer.SimpleIoBuffer;
import com.sonf.core.session.AttributeKey;
import com.sonf.core.session.IOSession;
import com.sonf.filter.IProtocolDecoder;
import com.sonf.filter.IProtocolEncoder;
import com.sonf.filter.IProtocolOutput;
import com.sonf.filter.ProtocolFilter;
import com.yynie.myutils.Logger;
import com.yynie.myutils.StringUtils;

import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static com.spde.sclauncher.SCConfig.DISABLE_CIPHER;

public class AesCipherFilter extends ProtocolFilter {
    private final Logger log = Logger.get(AesCipherFilter.class, Logger.Level.INFO);
    private final int MAX_BODY_LEN = 2 * 1024;  //接收到的数据超过2k就不行了
    private final String BODY_END = "\r\n"; //发送的密文以0d0a结尾
    private Cipher cipher;
    private Charset charset = Charset.forName("UTF-8");
    private String mode = "CBC";
    private String padding = "PKCS5Padding";  //ZeroPadding,PKCS5Padding
    private String KEY = "KEYKEYKEYKEYKEYK";
    private String IV = "IVIVIVIVIVIVIVIV";
    private String MTAG = "";
    private String VER = "";

    public AesCipherFilter(){
        setEncoder(new AesCipherEncoder());
        setDecoder(new AesCipherDecoder());
        log.i("create AesCipherFilter charset=" + charset.name() + ", mode=" + mode + ", padding=" + padding);
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset newCharset) {
        charset = newCharset;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getPadding() {
        return padding;
    }

    public void setPadding(String padding) {
        this.padding = padding;
    }

    public String getKEY() {
        return KEY;
    }

    public void setKEY(String KEY) {
        this.KEY = KEY;
    }

    public String getIV() {
        return IV;
    }

    public void setIV(String IV) {
        this.IV = IV;
    }

    public String getMTAG() {
        return MTAG;
    }

    public void setMTAG(String MTAG) {
        this.MTAG = MTAG;
    }

    public String getVER() {
        return VER;
    }

    public void setVER(String VER) {
        this.VER = VER;
    }

    private Cipher getCipher(){
        if(cipher == null) {
            String realPadding = padding;
            if(StringUtils.equals(realPadding, "ZeroPadding")){
                realPadding = "NoPadding";
            }
            String transformation = "AES/" + mode + "/" + realPadding;
            try {
                cipher = Cipher.getInstance(transformation);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            }finally {
                if(cipher == null){
                    throw new RuntimeException("create Cipher failed with transformation =" + transformation);
                }
            }
        }
        return cipher;
    }

    private class AesCipherEncoder implements IProtocolEncoder {
        @Override
        public void encode(IOSession session, Object message, IProtocolOutput out) throws Exception {
            if(message instanceof String){
                String plain = (String) message;
                String encrypt = doEncrypt(plain);
                if(DISABLE_CIPHER){
                    encrypt = plain;
                }
                if(encrypt != null){
                    if(StringUtils.isNotBlank(MTAG)){
                    encrypt += "#" + MTAG + "#" + VER;
                    }
                    encrypt += BODY_END;
                    IoBuffer ioBuffer = new SimpleIoBuffer();
                    ioBuffer.allocate(encrypt.length());
                    ioBuffer.putString(encrypt, charset.newEncoder());
                    ioBuffer.flip();
                    out.write(ioBuffer);
                    log.d(encrypt);
                }
            }else{
                throw new Exception("String expected but " + message.getClass().getSimpleName() + " input!");
            }
        }

        private String doEncrypt(String plain) throws Exception{
            if(StringUtils.isBlank(plain)) return null;
            Cipher aes = getCipher();
            byte[] plainText = plain.getBytes(charset.name());
            if(StringUtils.equals(padding, "ZeroPadding")){
                int blockSize = aes.getBlockSize();
                int plainLen = plainText.length;
                if (plainLen % blockSize != 0) {
                    plainLen = plainLen + (blockSize - (plainLen % blockSize));
                }
                byte[] paddingPlainText = new byte[plainLen];
                System.arraycopy(plainText, 0, paddingPlainText, 0, plainText.length);
                plainText = paddingPlainText;
            }
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(IV.getBytes());
            aes.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = aes.doFinal(plainText);
            String encryptedString = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            return encryptedString;
        }

        @Override
        public void dispose(IOSession session) throws Exception {
        }
    }

    private class AesCipherDecoder implements IProtocolDecoder {
        private final AttributeKey DECODER_STATE_ATT = new AttributeKey(AesCipherDecoder.class, "state.att");
        private final AttributeKey PARTIAL_BODY_ATT = new AttributeKey(AesCipherDecoder.class, "part.body.att");

        @Override
        public void decode(IOSession session, IoBuffer in, IProtocolOutput out) throws Exception {
            DecoderState state = (DecoderState) session.getAttribute(DECODER_STATE_ATT);
            if (null == state) {
                state = DecoderState.NEW;
                session.setAttribute(DECODER_STATE_ATT, state);
            }
            switch (state){
                case NEW:{
                    String raw = new String(in.buf().array(), in.position(), in.remaining());
                    int end = raw.indexOf("\r\n");
                    if(end < 0){
                        session.setAttribute(PARTIAL_BODY_ATT, raw);
                        session.setAttribute(DECODER_STATE_ATT, DecoderState.BODY);
                        in.position(in.limit());
                    }else{
                        String body = raw.substring(0, end);
                        in.position(in.position() + end + 2);
                        int pos = body.indexOf("#");
                        if(pos > 0){
                            body = body.substring(0, pos);
                        }
                        String plain = DISABLE_CIPHER ? body : doDecrypt(body);

                        if(plain != null) {
                            IoBuffer buf = new SimpleIoBuffer();
                            buf.allocate(plain.length());
                            buf.putString(plain, Charset.forName("UTF-8").newEncoder());
                            buf.flip();
                            out.write(buf);
                        }
                        session.setAttribute(DECODER_STATE_ATT, DecoderState.NEW);
                    }
                    break;
                }
                case BODY:{
                    String partial = (String) session.removeAttribute(PARTIAL_BODY_ATT);
                    String raw = new String(in.buf().array(), in.position(), in.remaining());
                    int end = raw.indexOf("\r\n");
                    if(end < 0){
                        partial += raw;
                        session.setAttribute(PARTIAL_BODY_ATT, partial);
                        in.position(in.limit());
                        if(partial.length() > MAX_BODY_LEN){
                            session.setAttribute(DECODER_STATE_ATT, DecoderState.NEW);
                            session.removeAttribute(PARTIAL_BODY_ATT);
                            throw new Exception("Packet length exceed!, max length = " + MAX_BODY_LEN + " bytes");
                        }
                    }else{
                        String body = partial + raw.substring(0, end);
                        in.position(in.position() + end + 2);
                        String plain = doDecrypt(body);
                        if(plain != null){
                            IoBuffer buf = new SimpleIoBuffer();
                            buf.allocate(plain.length());
                            buf.putString(plain, Charset.forName("UTF-8").newEncoder());
                            buf.flip();
                            out.write(buf);
                        }
                        session.setAttribute(DECODER_STATE_ATT, DecoderState.NEW);
                    }
                    break;
                }
                default:
                    throw new Exception("Unknown decode state:" + state);
            }
        }

        private String doDecrypt(String crypt) throws Exception {
            if(StringUtils.isBlank(crypt)) return null;
            Cipher aes = getCipher();
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(IV.getBytes());
            aes.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] cryptBytes = Base64.decode(crypt.getBytes(charset.name()), Base64.NO_WRAP);
            byte[] plainBytes = aes.doFinal(cryptBytes);
            int paddingLength = 0;
            if(StringUtils.equals(padding, "ZeroPadding")){
                int blockSize = aes.getBlockSize();
                for(paddingLength = 0; paddingLength < blockSize; paddingLength++){
                    if(plainBytes[plainBytes.length  - 1 - paddingLength] == 0){
                        continue;
                    }else{
                        break;
                    }
                }
            }
            String plain = new String(plainBytes, 0, plainBytes.length - paddingLength, charset.name());
            return plain;
        }

        @Override
        public void finishDecode(IOSession session, IProtocolOutput out) throws Exception {
            session.removeAttribute(DECODER_STATE_ATT);
            session.removeAttribute(PARTIAL_BODY_ATT);
        }

        @Override
        public void dispose(IOSession session) throws Exception {
            session.removeAttribute(DECODER_STATE_ATT);
            session.removeAttribute(PARTIAL_BODY_ATT);
        }
    }
}