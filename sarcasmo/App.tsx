import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Linking, NativeEventEmitter, NativeModules } from 'react-native';

const App = () => {
  const [charCount, setCharCount] = useState(0);

  useEffect(() => {
    const eventEmitter = new NativeEventEmitter(NativeModules.EventoReact);
    const eventListener = eventEmitter.addListener('TextAnalysisEvent', (eventData) => {
      const data = JSON.parse(eventData);
      setCharCount(data.compound); 
    });

    return () => {
      eventListener.remove();
    };
  }, []);

  const openAccessibilitySettings = () => {
    Linking.openSettings();
  };

  return (
    <View style={styles.container}>
      <Text style={styles.header}>Interpretación de textos</Text>
      <View style={styles.analysisContainer}>
        <Text style={styles.analysisText}>
          {charCount !== null ? `Valor polar : ${charCount}` : 'Leyendo...'}
        </Text>
      </View>
      <TouchableOpacity
        style={styles.button}
        onPress={openAccessibilitySettings}
      >
        <Text style={styles.buttonText}>Opciones de Accesibilidad</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  header: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  analysisContainer: {
    justifyContent: 'center',
    alignItems: 'center',
    margin: 20,
  },
  analysisText: {
    fontSize: 16,
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 15,
    borderRadius: 8,
  },
  buttonText: {
    color: '#fff',
    fontSize: 18,
  },
});

export default App;
