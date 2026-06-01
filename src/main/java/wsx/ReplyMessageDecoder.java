package wsx;

import org.springframework.stereotype.Component;

@Component
public final class ReplyMessageDecoder extends MessageDecoder<ReplyMessage> {

    @Override
    protected Class<ReplyMessage> messageClass() {
        return ReplyMessage.class;
    }
}
