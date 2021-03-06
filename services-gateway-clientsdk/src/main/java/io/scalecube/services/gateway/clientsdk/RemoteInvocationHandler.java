package io.scalecube.services.gateway.clientsdk;

import io.scalecube.services.gateway.clientsdk.exceptions.ClientErrorMapper;
import io.scalecube.services.methods.MethodInfo;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import reactor.core.Exceptions;

public class RemoteInvocationHandler implements InvocationHandler {

  private final ClientTransport transport;
  private final Map<Method, MethodInfo> methods;
  private final ClientCodec codec;
  private final ClientErrorMapper errorMapper;

  /**
   * Constructor for remote invocation handler.
   *
   * @param transport client sdk transport implementation
   * @param methods methods
   * @param codec client message codec
   */
  public RemoteInvocationHandler(
      ClientTransport transport,
      Map<Method, MethodInfo> methods,
      ClientCodec codec,
      ClientErrorMapper errorMapper) {
    this.transport = transport;
    this.methods = methods;
    this.codec = codec;
    this.errorMapper = errorMapper;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    MethodInfo methodInfo = methods.get(method);

    ClientMessage request =
        ClientMessage.builder()
            .qualifier(methodInfo.qualifier())
            .data(methodInfo.parameterCount() != 0 ? args[0] : null)
            .build();

    Class<?> responseType = methodInfo.parameterizedReturnType();

    switch (methodInfo.communicationMode()) {
      case REQUEST_RESPONSE:
        return transport
            .requestResponse(request)
            .map(response -> codec.decodeData(response, responseType))
            .map(this::throwIfError)
            .map(ClientMessage::data);
      case REQUEST_STREAM:
        return transport
            .requestStream(request)
            .map(clientMessage -> codec.decodeData(clientMessage, responseType))
            .map(this::throwIfError)
            .map(ClientMessage::data);
      default:
        throw new IllegalArgumentException("Unsupported communication mode");
    }
  }

  private ClientMessage throwIfError(ClientMessage response) {
    if (response.isError() && response.hasData(ErrorData.class)) {
      throw Exceptions.propagate(errorMapper.toError(response));
    }

    return response;
  }
}
