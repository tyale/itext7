package com.itextpdf.core.parser;

import com.itextpdf.basics.io.PdfTokenizer;
import com.itextpdf.core.pdf.*;
import com.itextpdf.core.pdf.filters.FilterHandlers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods to help with processing of inline images
 * @since 5.0.4
 */
public final class InlineImageUtils {
    private final static Logger LOGGER = LoggerFactory.getLogger(InlineImageUtils.class.getName());
    private InlineImageUtils(){}

    /**
     * Simple class in case users need to differentiate an exception from processing
     * inline images vs other exceptions
     * @since 5.0.4
     */
    public static class InlineImageParseException extends IOException{

        private static final long serialVersionUID = 233760879000268548L;

        public InlineImageParseException(String message) {
            super(message);
        }

    }

    /**
     * Map between key abbreviations allowed in dictionary of inline images and their
     * equivalent image dictionary keys
     */
    private final static Map<PdfName, PdfName> inlineImageEntryAbbreviationMap;
    static { // static initializer
        inlineImageEntryAbbreviationMap = new HashMap<PdfName, PdfName>();

        // allowed entries - just pass these through
        inlineImageEntryAbbreviationMap.put(PdfName.BitsPerComponent, PdfName.BitsPerComponent);
        inlineImageEntryAbbreviationMap.put(PdfName.ColorSpace, PdfName.ColorSpace);
        inlineImageEntryAbbreviationMap.put(PdfName.Decode, PdfName.Decode);
        inlineImageEntryAbbreviationMap.put(PdfName.DecodeParms, PdfName.DecodeParms);
        inlineImageEntryAbbreviationMap.put(PdfName.Filter, PdfName.Filter);
        inlineImageEntryAbbreviationMap.put(PdfName.Height, PdfName.Height);
        inlineImageEntryAbbreviationMap.put(PdfName.ImageMask, PdfName.ImageMask);
        inlineImageEntryAbbreviationMap.put(PdfName.Intent, PdfName.Intent);
        inlineImageEntryAbbreviationMap.put(PdfName.Interpolate, PdfName.Interpolate);
        inlineImageEntryAbbreviationMap.put(PdfName.Width, PdfName.Width);

        // abbreviations - transform these to corresponding correct values
        inlineImageEntryAbbreviationMap.put(new PdfName("BPC"), PdfName.BitsPerComponent);
        inlineImageEntryAbbreviationMap.put(new PdfName("CS"), PdfName.ColorSpace);
        inlineImageEntryAbbreviationMap.put(new PdfName("D"), PdfName.Decode);
        inlineImageEntryAbbreviationMap.put(new PdfName("DP"), PdfName.DecodeParms);
        inlineImageEntryAbbreviationMap.put(new PdfName("F"), PdfName.Filter);
        inlineImageEntryAbbreviationMap.put(new PdfName("H"), PdfName.Height);
        inlineImageEntryAbbreviationMap.put(new PdfName("IM"), PdfName.ImageMask);
        inlineImageEntryAbbreviationMap.put(new PdfName("I"), PdfName.Interpolate);
        inlineImageEntryAbbreviationMap.put(new PdfName("W"), PdfName.Width);
    }

    /**
     * Map between value abbreviations allowed in dictionary of inline images for COLORSPACE
     */
    private static final Map<PdfName, PdfName> inlineImageColorSpaceAbbreviationMap;
    static {
        inlineImageColorSpaceAbbreviationMap = new HashMap<PdfName, PdfName>();

        inlineImageColorSpaceAbbreviationMap.put(new PdfName("G"), PdfName.DeviceGray);
        inlineImageColorSpaceAbbreviationMap.put(new PdfName("RGB"), PdfName.DeviceRGB);
        inlineImageColorSpaceAbbreviationMap.put(new PdfName("CMYK"), PdfName.DeviceCMYK);
        inlineImageColorSpaceAbbreviationMap.put(new PdfName("I"), PdfName.Indexed);
    }

    /**
     * Map between value abbreviations allowed in dictionary of inline images for FILTER
     */
    private static final Map<PdfName, PdfName> inlineImageFilterAbbreviationMap;
    static {
        inlineImageFilterAbbreviationMap = new HashMap<PdfName, PdfName>();

        inlineImageFilterAbbreviationMap.put(new PdfName("AHx"), PdfName.ASCIIHexDecode);
        inlineImageFilterAbbreviationMap.put(new PdfName("A85"), PdfName.ASCII85Decode);
        inlineImageFilterAbbreviationMap.put(new PdfName("LZW"), PdfName.LZWDecode);
        inlineImageFilterAbbreviationMap.put(new PdfName("Fl"), PdfName.FlateDecode);
        inlineImageFilterAbbreviationMap.put(new PdfName("RL"), PdfName.RunLengthDecode);
        inlineImageFilterAbbreviationMap.put(new PdfName("CCF"), PdfName.CCITTFaxDecode);
        inlineImageFilterAbbreviationMap.put(new PdfName("DCT"), PdfName.DCTDecode);
    }

    /**
     * Parses an inline image from the provided content parser.  The parser must be positioned immediately following the BI operator in the content stream.
     * The parser will be left with current position immediately following the EI operator that terminates the inline image
     * @param ps the content parser to use for reading the image.
     * @param colorSpaceDic a color space dictionary
     * @return the parsed image
     * @throws IOException if anything goes wring with the parsing
     * @throws InlineImageParseException if parsing of the inline image failed due to issues specific to inline image processing
     */
    public static InlineImageInfo parseInlineImage(PdfContentParser ps, PdfDictionary colorSpaceDic) throws IOException{
        PdfDictionary inlineImageDictionary = parseInlineImageDictionary(ps);
        byte[] samples = parseInlineImageSamples(inlineImageDictionary, colorSpaceDic, ps);
        return new InlineImageInfo(samples, inlineImageDictionary);
    }

    /**
     * Parses the next inline image dictionary from the parser.  The parser must be positioned immediately following the EI operator.
     * The parser will be left with position immediately following the whitespace character that follows the ID operator that ends the inline image dictionary.
     * @param ps the parser to extract the embedded image information from
     * @return the dictionary for the inline image, with any abbreviations converted to regular image dictionary keys and values
     * @throws IOException if the parse fails
     */
    private static PdfDictionary parseInlineImageDictionary(PdfContentParser ps) throws IOException{
        // by the time we get to here, we have already parsed the BI operator
        PdfDictionary dictionary = new PdfDictionary();

        for(PdfObject key = ps.readPRObject(); key != null && !"ID".equals(key.toString()); key = ps.readPRObject()){
            PdfObject value = ps.readPRObject();

            PdfName resolvedKey = inlineImageEntryAbbreviationMap.get(key);
            if (resolvedKey == null)
                resolvedKey = (PdfName)key;

            dictionary.put(resolvedKey, getAlternateValue(resolvedKey, value));
        }

        int ch = ps.getTokeniser().read();
        if (!PdfTokenizer.isWhitespace(ch))
            throw new IOException("Unexpected character " + ch + " found after ID in inline image");

        return dictionary;
    }

    /**
     * Transforms value abbreviations into their corresponding real value
     * @param key the key that the value is for
     * @param value the value that might be an abbreviation
     * @return if value is an allowed abbreviation for the key, the expanded value for that abbreviation.  Otherwise, value is returned without modification
     */
    private static PdfObject getAlternateValue(PdfName key, PdfObject value){
        if (key == PdfName.Filter){
            if (value instanceof PdfName){
                PdfName altValue = inlineImageFilterAbbreviationMap.get(value);
                if (altValue != null)
                    return altValue;
            } else if (value instanceof PdfArray){
                PdfArray array = ((PdfArray)value);
                PdfArray altArray = new PdfArray();
                int count = array.size();
                for(int i = 0; i < count; i++){
                    altArray.add(getAlternateValue(key, array.get(i)));
                }
                return altArray;
            }
        } else if (key == PdfName.ColorSpace){
            PdfName altValue = inlineImageColorSpaceAbbreviationMap.get(value);
            if (altValue != null)
                return altValue;
        }

        return value;
    }

    /**
     * @param colorSpaceName the name of the color space. If null, a bi-tonal (black and white) color space is assumed.
     * @return the components per pixel for the specified color space
     */
    private static int getComponentsPerPixel(PdfName colorSpaceName, PdfDictionary colorSpaceDic){
        if (colorSpaceName == null)
            return 1;
        if (colorSpaceName.equals(PdfName.DeviceGray))
            return 1;
        if (colorSpaceName.equals(PdfName.DeviceRGB))
            return 3;
        if (colorSpaceName.equals(PdfName.DeviceCMYK))
            return 4;

        if (colorSpaceDic != null){
            PdfArray colorSpace = colorSpaceDic.getAsArray(colorSpaceName);
            if (colorSpace != null){
                if (PdfName.Indexed.equals(colorSpace.getAsName(0))){
                    return 1;
                }
            }
            else {
                PdfName tempName = colorSpaceDic.getAsName(colorSpaceName);
                if (tempName != null) {
                    return getComponentsPerPixel(tempName, colorSpaceDic);
                }
            }
        }

        throw new IllegalArgumentException("Unexpected color space " + colorSpaceName);
    }

    /**
     * Computes the number of unfiltered bytes that each row of the image will contain.
     * If the number of bytes results in a partial terminating byte, this number is rounded up
     * per the PDF specification
     * @param imageDictionary the dictionary of the inline image
     * @return the number of bytes per row of the image
     */
    private static int computeBytesPerRow(PdfDictionary imageDictionary, PdfDictionary colorSpaceDic){
        PdfNumber wObj = imageDictionary.getAsNumber(PdfName.Width);
        PdfNumber bpcObj = imageDictionary.getAsNumber(PdfName.BitsPerComponent);
        int cpp = getComponentsPerPixel(imageDictionary.getAsName(PdfName.ColorSpace), colorSpaceDic);

        int w = wObj.getIntValue();
        int bpc = bpcObj != null ? bpcObj.getIntValue() : 1;


        int bytesPerRow = (w * bpc * cpp + 7) / 8;

        return bytesPerRow;
    }

    /**
     * Parses the samples of the image from the underlying content parser, ignoring all filters.
     * The parser must be positioned immediately after the ID operator that ends the inline image's dictionary.
     * The parser will be left positioned immediately following the EI operator.
     * This is primarily useful if no filters have been applied.
     * @param imageDictionary the dictionary of the inline image
     * @param ps the content parser
     * @return the samples of the image
     * @throws IOException if anything bad happens during parsing
     */
    private static byte[] parseUnfilteredSamples(PdfDictionary imageDictionary, PdfDictionary colorSpaceDic, PdfContentParser ps) throws IOException{
        // special case:  when no filter is specified, we just read the number of bits
        // per component, multiplied by the width and height.
        if (imageDictionary.containsKey(PdfName.Filter))
            throw new IllegalArgumentException("Dictionary contains filters");

        PdfNumber h = imageDictionary.getAsNumber(PdfName.Height);

        int bytesToRead = computeBytesPerRow(imageDictionary, colorSpaceDic) * h.getIntValue();
        byte[] bytes = new byte[bytesToRead];
        PdfTokenizer tokeniser = ps.getTokeniser();

        int shouldBeWhiteSpace = tokeniser.read(); // skip next character (which better be a whitespace character - I suppose we could check for this)
        // from the PDF spec:  Unless the image uses ASCIIHexDecode or ASCII85Decode as one of its filters, the ID operator shall be followed by a single white-space character, and the next character shall be interpreted as the first byte of image data.
        // unfortunately, we've seen some PDFs where there is no space following the ID, so we have to capture this case and handle it
        int startIndex = 0;
        if (!PdfTokenizer.isWhitespace(shouldBeWhiteSpace) || shouldBeWhiteSpace == 0){ // tokeniser treats 0 as whitespace, but for our purposes, we shouldn't
            bytes[0] = (byte)shouldBeWhiteSpace;
            startIndex++;
        }
        for(int i = startIndex; i < bytesToRead; i++){
            int ch = tokeniser.read();
            if (ch == -1)
                throw new InlineImageParseException("End of content stream reached before end of image data");

            bytes[i] = (byte)ch;
        }
        PdfObject ei = ps.readPRObject();
        if (!ei.toString().equals("EI")) {
            // Some PDF producers seem to add another non-whitespace character after the image data.
            // Let's try to handle that case here.
            PdfObject ei2 = ps.readPRObject();
            if (!ei2.toString().equals("EI"))
                throw new InlineImageParseException("EI not found after end of image data");
        }
        return bytes;
    }

    /**
     * Parses the samples of the image from the underlying content parser, accounting for filters
     * The parser must be positioned immediately after the ID operator that ends the inline image's dictionary.
     * The parser will be left positioned immediately following the EI operator.
     * <b>Note:</b>This implementation does not actually apply the filters at this time
     * @param imageDictionary the dictionary of the inline image
     * @param ps the content parser
     * @return the samples of the image
     * @throws IOException if anything bad happens during parsing
     */
    private static byte[] parseInlineImageSamples(PdfDictionary imageDictionary, PdfDictionary colorSpaceDic, PdfContentParser ps) throws IOException{
        // by the time we get to here, we have already parsed the ID operator

        if (!imageDictionary.containsKey(PdfName.Filter)){
            return parseUnfilteredSamples(imageDictionary, colorSpaceDic, ps);
        }


        // read all content until we reach an EI operator surrounded by whitespace.
        // The following algorithm has two potential issues: what if the image stream
        // contains <ws>EI<ws> ?
        // Plus, there are some streams that don't have the <ws> before the EI operator
        // it sounds like we would have to actually decode the content stream, which
        // I'd rather avoid right now.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayOutputStream accumulated = new ByteArrayOutputStream();
        int ch;
        int found = 0;
        PdfTokenizer tokeniser = ps.getTokeniser();

        while ((ch = tokeniser.read()) != -1){
            if (found == 0 && PdfTokenizer.isWhitespace(ch)){
                found++;
                accumulated.write(ch);
            } else if (found == 1 && ch == 'E'){
                found++;
                accumulated.write(ch);
            } else if (found == 1 && PdfTokenizer.isWhitespace(ch)){
                // this clause is needed if we have a white space character that is part of the image data
                // followed by a whitespace character that precedes the EI operator.  In this case, we need
                // to flush the first whitespace, then treat the current whitespace as the first potential
                // character for the end of stream check.  Note that we don't increment 'found' here.
                baos.write(accumulated.toByteArray());
                accumulated.reset();
                accumulated.write(ch);
            } else if (found == 2 && ch == 'I'){
                found++;
                accumulated.write(ch);
            } else if (found == 3 && PdfTokenizer.isWhitespace(ch)){
                byte[] tmp = baos.toByteArray();
                if (inlineImageStreamBytesAreComplete(tmp, imageDictionary)){
                    return tmp;
                }
                baos.write(accumulated.toByteArray());
                accumulated.reset();

                baos.write(ch);
                found = 0;

            } else {
                baos.write(accumulated.toByteArray());
                accumulated.reset();

                baos.write(ch);
                found = 0;
            }
        }
        throw new InlineImageParseException("Could not find image data or EI");
    }

    private static boolean inlineImageStreamBytesAreComplete(byte[] samples, PdfDictionary imageDictionary){
        PdfReader.decodeBytes(samples, imageDictionary, FilterHandlers.getDefaultFilterHandlers());
        return true;
    }
}
